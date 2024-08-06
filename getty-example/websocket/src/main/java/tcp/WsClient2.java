package tcp;

import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.util.thread.ThreadPool;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class WsClient2 {

    static ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);
    private static String PROTOCOL = "TLSv1.2";

    public static void main(String[] args) throws Exception {
        //System.setProperty("javax.net.debug", "all");
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        WebSocketClientInst2 chatclient = new WebSocketClientInst2(new URI("ws://localhost:8888/echo"));
        //获取证书
        String pkPath = WsClient.class.getClassLoader().getResource("clientStore.jks")
                .getPath();
        //ssl配置
        SSLConfig sSLConfig = new SSLConfig();
        sSLConfig.setKeyFile(pkPath);
        sSLConfig.setKeyPassword("123456");
        sSLConfig.setKeystorePassword("123456");
        sSLConfig.setTrustFile(pkPath);
        sSLConfig.setTrustPassword("123456");
        //设置客户端模式
        sSLConfig.setClientMode(true);
        SSLContext sslContext = init(sSLConfig);
        SSLSocketFactory factory = sslContext.getSocketFactory();
        //chatclient.setSocketFactory(factory);

        chatclient.connectBlocking();
        boolean loop = false;
        int times = 0;
        while (loop) {
            times++;
            if (ReadyState.OPEN.equals(chatclient.getReadyState())) {
                chatclient.send("123".getBytes());  //发送二进制文件
                //chatclient.sendPing();
                Thread.sleep(1000);
            } else {
                System.out.println("还没ready, 继续进行中");
                if (times <= 10) {
                    Thread.sleep(1000);
                } else {
                    System.out.println("超时");
                    //break;
                }
            }
        }
    }


    /**
     * 初始化
     *
     * @param config
     */
    private static SSLContext init(SSLConfig config) {

        SSLContext sslContext = null;
        try {

            KeyManager[] keyManagers = null;
            if (config.getKeyFile() != null) {
                // 密钥库KeyStore
                KeyStore ks = KeyStore.getInstance("JKS");
                // 加载服务端的KeyStore，用于检查密钥库完整性的密码
                ks.load(new FileInputStream(config.getKeyFile()), config.getKeystorePassword().toCharArray());
                // 初始化密钥管理器
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, config.getKeyPassword().toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            //初始化签名证书管理器
            TrustManager[] trustManagers;
            if (config.getTrustFile() != null) {
                // 密钥库KeyStore
                KeyStore ts = KeyStore.getInstance("JKS");
                // 加载信任库
                ts.load(new FileInputStream(config.getTrustFile()), config.getTrustPassword().toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            } else {
                trustManagers = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
            }

            //初始化上下文
            sslContext = SSLContext.getInstance(PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return sslContext;
    }

}


class WebSocketClientInst2 extends WebSocketClient {

    public WebSocketClientInst2(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("onOpen");

    }

    @Override
    public void onMessage(String message) {
        System.out.println("got: " + message);

    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        System.out.println("bytes: " + new String(bytes.array()));
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("onClose");

    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onWebsocketPing(WebSocket conn, Framedata f) {
        System.out.println("onWebsocketPing");
    }


    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        System.out.println("onWebsocketPong");
    }
}

