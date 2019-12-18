package test.sslfacade;

import com.gettyio.core.handler.ssl.sslfacade.*;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.concurrent.Semaphore;

/**
 * This test check if the communication over the SSLFacade works. Actually it was written in the first place more likely to check the library.
 */
public class SSLFacadeTest {

    public static final String SERVER_TAG = "server";
    public static final String CLIENT_TAG = "client";

    public static final String JKS_FILE_PASSWORD = "123456";
    public static final String JKS_FILE = "src/test/resources/test.jks";

    private final ITaskHandler taskHandler = new DefaultTaskHandler();

    private final CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    private final CharsetDecoder decoder = Charset.forName("US-ASCII").newDecoder();

    private Semaphore sslClientSem;
    private Semaphore sslServerSem;
    private ISSLFacade sslClient;
    private ISSLFacade sslServer;
    private SSLListener clientListener;
    private SSLListener serverListener;
    private SSLContext sslCtx;

    class SSLListener implements ISSLListener {

        private final ISSLFacade sslPeer;
        private final String who;
        private final ByteBuffer buffer = ByteBuffer.allocate(1024 * 5);
        private boolean autoflush = true;

        public SSLListener(final String who, final ISSLFacade ssl) {
            this.sslPeer = ssl;
            this.who = who;
        }


        @Override
        public void onWrappedData(ByteBuffer wrappedBytes) {
            try {
                log(who + " onWrappedData: pass data " + wrappedBytes );
                buffer.put(wrappedBytes);
                if (autoflush) {
                    flush();
                }
                log(who + " onWrappedData: data decrypted " + wrappedBytes );
            } catch (Exception ex) {
                //log(who + " onWrappedData: Error while sending data to peer; " + ex);
            }
        }

        @Override
        public void onPlainData(ByteBuffer plainBytes) {


        }

        public void flush() throws SSLException {
            buffer.flip();
            ByteBuffer bb = ByteBuffer.allocate(buffer.capacity());
            bb.put(buffer);
            buffer.compact();

            bb.flip();
            sslPeer.decrypt(bb);
        }
    }

    public SSLFacadeTest() {
    }

    private static void log(final String message) {
        System.out.println("[SSLFacadeTest]: " + message);
    }

    public ISSLFacade createSSL(final String who, boolean client) {
        ISSLFacade ssl = new SSLFacade(sslCtx, client, false, taskHandler);
        attachHandshakeListener(who, ssl);

        return ssl;
    }

    public void attachHandshakeListener(final String who, final ISSLFacade ssl) {
        ssl.setHandshakeCompletedListener(new IHandshakeCompletedListener() {
            @Override
            public void onComplete() {
                log(who + ": Handshake completed.");
//                notifications.add(END_OF_HANDSHAKE);
//                sem.release();
                //log(who + ": semaphore released " + sem);
            }
        });
    }

    private SSLListener crateListener(final String who, final ISSLFacade sslPeer) {
        return new SSLListener(who, sslPeer);
    }


    @Before
    public void setUp() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        String keyStoreFile = JKS_FILE;
        String trustStoreFile = JKS_FILE;
        String passw = JKS_FILE_PASSWORD;

        char[] passphrase = passw.toCharArray();

        ks.load(new FileInputStream(keyStoreFile), passphrase);

        ts.load(new FileInputStream(trustStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);


        sslClientSem = new Semaphore(0);
        sslServerSem = new Semaphore(0);

        sslClient = createSSL(CLIENT_TAG, true);
        sslServer = createSSL(SERVER_TAG, false);

        log("== Init SSL listeners");
        clientListener = crateListener(CLIENT_TAG, sslServer);
        serverListener = crateListener(SERVER_TAG, sslClient);
        sslClient.setSSLListener(clientListener);
        sslServer.setSSLListener(serverListener);

//    cleintIn1 = CharBuffer.wrap(HELLO_FROM_CLIENT_1);
//    serverIn1 = CharBuffer.wrap(HELLO_FROM_SERVER_1);
//    cleintIn2 = CharBuffer.wrap(HELLO_FROM_CLIENT_2);
//    serverIn2 = CharBuffer.wrap(HELLO_FROM_SERVER_2);
//    cleintIn3 = CharBuffer.wrap(HELLO_FROM_CLIENT_3);

    }

    /**
     * @throws SSLException
     * @throws CharacterCodingException
     * @throws InterruptedException
     */
    @Test
    public void check_simpleCommunicationScenario() throws SSLException, CharacterCodingException, InterruptedException, IOException {
        // given

        // when
        log("== Client started handshake");
        sslClient.beginHandshake();
        log("== Server started handshake");
        sslServer.beginHandshake();

//    log("== Client waits untill handshake is done on " + sslClientSem);
//    sslClientSem.acquire();
//
//    log("== Server waits untill handshake is done on " + sslServerSem);
//    sslServerSem.acquire();
//
//    log("== Sending first message (full duplex)");
//    sslClient.encrypt(encoder.encode(cleintIn1));
//    sslServer.encrypt(encoder.encode(serverIn1));
//
//    log("== Wait untill the first message arrived");
//    sslClientSem.acquire();
//    sslServerSem.acquire();
//
//    log("== Sending second message to server");
//    sslClient.encrypt(encoder.encode(cleintIn2));
//    sslServerSem.acquire();
//
//    log("== Sending second message to client");
//    sslServer.encrypt(encoder.encode(serverIn2));
//    sslClientSem.acquire();
//
//    log("== Close connection on client side");
//    attachSessionCloseListener(CLIENT_TAG, sslClient, clientNotifications, sslClientSem);
//    attachSessionCloseListener(SERVER_TAG, sslServer, serverNotifications, sslServerSem);
//    sslClient.close();
//
//    log("== Wait server has received end of session on sem " + sslClientSem);
//    sslServerSem.acquire();
//
//    //then
//    Assertions.assertThat(clientNotifications)
//            .hasSize(4)
//            .containsExactly(END_OF_HANDSHAKE, HELLO_FROM_SERVER_1, HELLO_FROM_SERVER_2, END_OF_SESSION);
//
//    Assertions.assertThat(serverNotifications)
//            .hasSize(4)
//            .containsExactly(END_OF_HANDSHAKE, HELLO_FROM_CLIENT_1, HELLO_FROM_CLIENT_2, END_OF_SESSION);
    }


}
