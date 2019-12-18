package test.sslfacade;

import com.gettyio.core.handler.ssl.sslfacade.*;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * This test check if the communication over the SSLFacade works. Actually it was written in the first place more likely to check the library.
 */
public class SSLFacadePerformanceTest {

    public static final String SERVER_TAG = "server";
    public static final String CLIENT_TAG = "client";

    public static final String JKS_FILE_PASSWORD = "123456";
    public static final String JKS_FILE = "src/test/resources/test.jks";

    public static final String END_OF_SESSION = "END_OF_SESSION";
    public static final String END_OF_HANDSHAKE = "END_OF_HANDSHAKE";

    private static int BUFFER_SIZE = 1024 * 5;

    private final ITaskHandler taskHandler = new DefaultTaskHandler();

    private final CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    private final CharsetDecoder decoder = Charset.forName("US-ASCII").newDecoder();

    private final CharBuffer cleintIn1 = CharBuffer.allocate(BUFFER_SIZE);

    private List<String> clientNotifications;
    private List<String> serverNotifications;
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
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE * 2);
        private boolean autoflush = true;

        public SSLListener(final String who, final ISSLFacade ssl, List<String> notifications, final Semaphore sem) {
            this.sslPeer = ssl;
            this.who = who;
        }

        public void setAutoflush(boolean autoflush) {
            this.autoflush = autoflush;
        }

        @Override
        public void onWrappedData(ByteBuffer wrappedBytes) {
            try {
                buffer.put(wrappedBytes);
                if (autoflush) {
                    flush();
                }
            } catch (SSLException ex) {
                log(who + " onWrappedData: Error while sending data to peer; " + ex);
            }
        }

        @Override
        public void onPlainData(ByteBuffer plainBytes) {
            try {
                CharBuffer decodedString = decoder.decode(plainBytes);
                log("Decoded data lenght: " + decodedString.length());
            } catch (CharacterCodingException ex) {
                log(who + ": !ERROR! could not decode data received from peer");
            }
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

    ;

    public SSLFacadePerformanceTest() {
    }

    private static void log(final String message) {
        System.out.println("[SSLFacadeTest]: " + message);
    }

    public ISSLFacade createSSL(final String who, boolean client, final List<String> notifications, final Semaphore sem) {
        ISSLFacade ssl = new SSLFacade(sslCtx, client, false, taskHandler);
        attachHandshakeListener(who, ssl, notifications, sem);

        return ssl;
    }

    public void attachHandshakeListener(final String who, final ISSLFacade ssl, final List<String> notifications, final Semaphore sem) {
        ssl.setHandshakeCompletedListener(new IHandshakeCompletedListener() {
            @Override
            public void onComplete() {
                log(who + ": Handshake completed.");
                notifications.add(END_OF_HANDSHAKE);
                sem.release();
                log(who + ": semaphore released " + sem);
            }
        });
    }

    private SSLListener crateListener(final String who, final ISSLFacade sslPeer, final List<String> notificatons, final Semaphore sem) {
        return new SSLListener(who, sslPeer, notificatons, sem);
    }

    private void attachSessionCloseListener(final String who, final ISSLFacade sslServer, final List<String> notifications, final Semaphore sem) {
        sslServer.setCloseListener(new ISessionClosedListener() {
            @Override
            public void onSessionClosed() {
                log(who + ": peer closed the session. Post notification on sem : " + sem);
                notifications.add(END_OF_SESSION);
                sem.release();
                log(who + ": peer closed the session. Sem notified : " + sem);
            }
        });
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

        clientNotifications = new LinkedList<String>();
        serverNotifications = new LinkedList<String>();

        sslClientSem = new Semaphore(0);
        sslServerSem = new Semaphore(0);

        sslClient = createSSL(CLIENT_TAG, true, clientNotifications, sslClientSem);
        sslServer = createSSL(SERVER_TAG, false, serverNotifications, sslServerSem);

        log("== Init SSL listeners");
        clientListener = crateListener(CLIENT_TAG, sslServer, clientNotifications, sslClientSem);
        serverListener = crateListener(SERVER_TAG, sslClient, serverNotifications, sslServerSem);
        sslClient.setSSLListener(clientListener);
        sslServer.setSSLListener(serverListener);

    }

    /**
     * @throws SSLException
     * @throws CharacterCodingException
     * @throws InterruptedException
     */
    @Test
    public void shall_transferBigStreamOfMessages() throws SSLException, CharacterCodingException, InterruptedException, IOException {
        // given

        // when
        log("== Client started handshake");
        sslClient.beginHandshake();
        log("== Server started handshake");
        sslServer.beginHandshake();

        log("== Client waits untill handshake is done on " + sslClientSem);
        sslClientSem.acquire();

        log("== Server waits untill handshake is done on " + sslServerSem);
        sslServerSem.acquire();

        // Set the autoflush back so the close operation shoudl be done.
        clientListener.setAutoflush(true);

        log("== Sending messages");
        clientListener.setAutoflush(false);

        long previousTime = new Date().getTime();
        long now = new Date().getTime();
        for (int i = 0; i < 10000; i++) {
            sslClient.encrypt(encoder.encode(CharBuffer.allocate(BUFFER_SIZE)));
            clientListener.flush(); // check what happends if all encoded data is passed in one message
            log("+ " + i + "dT=" + (now - previousTime));
            previousTime = now;
            now = new Date().getTime();
        }

        log("== Close connection on client side");
        attachSessionCloseListener(CLIENT_TAG, sslClient, clientNotifications, sslClientSem);
        attachSessionCloseListener(SERVER_TAG, sslServer, serverNotifications, sslServerSem);
        sslClient.close();

        log("== Wait server has received end of session on sem " + sslClientSem);

        //then
        Assertions.assertThat(clientNotifications)
                .containsExactly(END_OF_HANDSHAKE, END_OF_SESSION);
    }
}
