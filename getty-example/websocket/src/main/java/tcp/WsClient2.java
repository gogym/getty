package tcp;

import com.gettyio.core.handler.ssl.SSLConfig;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket SSL 客户端测试示例。
 * <p>
 * 使用 SSL/TLS 连接服务端（需服务端也启用 SSL）。
 * 测试场景与 {@link WsClient} 相同，但走 SSL 加密通道。
 * </p>
 */
public class WsClient2 {

    private static final AtomicInteger echoCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        SSLClient client = new SSLClient(new URI("ws://localhost:8888/echo"));

        // 配置 SSL
        String pkPath = WsClient2.class.getClassLoader().getResource("clientStore.jks").getPath();
        SSLConfig sslConfig = new SSLConfig();
        sslConfig.setKeyFile(pkPath);
        sslConfig.setKeyPassword("123456");
        sslConfig.setKeystorePassword("123456");
        sslConfig.setTrustFile(pkPath);
        sslConfig.setTrustPassword("123456");
        sslConfig.setClientMode(true);
        SSLContext sslContext = initSSL(sslConfig);
        client.setSocketFactory(sslContext.getSocketFactory());

        client.connectBlocking(5, java.util.concurrent.TimeUnit.SECONDS);

        if (!client.isOpen()) {
            System.out.println("SSL 连接失败！");
            return;
        }

        System.out.println("\n========== 开始 SSL WebSocket 测试 ==========\n");

        // 文本帧
        System.out.println("[发送] 文本消息");
        client.send("Hello SSL WebSocket!");
        Thread.sleep(500);

        // 二进制帧
        byte[] binData = new byte[256];
        for (int i = 0; i < binData.length; i++) binData[i] = (byte) (i & 0xFF);
        System.out.println("[发送] 二进制消息, 长度=" + binData.length);
        client.send(binData);
        Thread.sleep(500);

        // Ping
        System.out.println("[发送] Ping 心跳");
        client.sendPing();
        Thread.sleep(500);

        // 中等长度文本（测试 16 位扩展长度）
        char[] midChars = new char[500];
        java.util.Arrays.fill(midChars, 'X');
        String midText = new String(midChars);
        System.out.println("[发送] 中等文本(500字节)");
        client.send(midText);
        Thread.sleep(500);

        // 关闭
        System.out.println("[发送] Close 关闭帧");
        client.closeBlocking();
        Thread.sleep(500);

        System.out.println("\n========== SSL 测试完成, 共收到 " + echoCount.get() + " 条回显 ==========");
    }

    private static SSLContext initSSL(SSLConfig config) {
        try {
            KeyManager[] keyManagers = null;
            if (config.getKeyFile() != null) {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(new FileInputStream(config.getKeyFile()), config.getKeystorePassword().toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, config.getKeyPassword().toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            TrustManager[] trustManagers;
            if (config.getTrustFile() != null) {
                KeyStore ts = KeyStore.getInstance("JKS");
                ts.load(new FileInputStream(config.getTrustFile()), config.getTrustPassword().toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            } else {
                trustManagers = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("SSL 初始化失败", e);
        }
    }


    static class SSLClient extends WebSocketClient {
        public SSLClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("[SSL客户端] 连接成功");
        }

        @Override
        public void onMessage(String message) {
            int count = echoCount.incrementAndGet();
            System.out.println("[SSL客户端] 收到文本回显 #" + count + ", 长度=" + message.length());
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            int count = echoCount.incrementAndGet();
            System.out.println("[SSL客户端] 收到二进制回显 #" + count + ", 长度=" + bytes.remaining());
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("[SSL客户端] 连接关闭, code=" + code);
        }

        @Override
        public void onError(Exception ex) {
            System.out.println("[SSL客户端] 错误: " + ex.getMessage());
        }

        @Override
        public void onWebsocketPong(WebSocket conn, Framedata f) {
            System.out.println("[SSL客户端] 收到 Pong 帧 ✓");
        }
    }
}
