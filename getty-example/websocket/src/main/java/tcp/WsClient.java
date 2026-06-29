package tcp;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 客户端全场景测试。
 * <p>
 * 连接成功后依次测试各种帧类型和不同长度的负载，服务端回显后验证结果。
 * 测试场景：
 * <ol>
 *   <li>短文本帧（&lt; 126 字节，7 位长度字段）</li>
 *   <li>中文文本帧</li>
 *   <li>中等文本帧（126 ~ 65535 字节，16 位扩展长度）</li>
 *   <li>大文本帧（&gt; 65535 字节，64 位扩展长度）</li>
 *   <li>短二进制帧（&lt; 126 字节）</li>
 *   <li>中等二进制帧（126 ~ 65535 字节）</li>
 *   <li>大二进制帧（&gt; 65535 字节）</li>
 *   <li>Ping / Pong 心跳</li>
 *   <li>Close 关闭帧</li>
 * </ol>
 * </p>
 */
public class WsClient {

    /** 收到的回显计数 */
    private static final AtomicInteger echoCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        TestClient client = new TestClient(new URI("ws://localhost:8888/echo"));
        client.connectBlocking(5, TimeUnit.SECONDS);

        if (!client.isOpen()) {
            System.out.println("连接失败！");
            return;
        }

        System.out.println("\n========== 开始 WebSocket 全场景测试 ==========\n");

        // ---- 1. 短文本帧（< 126 字节）----
        testText(client, "短文本", "Hello, WebSocket!");

        // ---- 2. 中文文本帧 ----
        testText(client, "中文文本", "你好，Getty WebSocket 框架！支持中文消息测试。");

        // ---- 3. 空文本帧 ----
        testText(client, "空文本", "");

        // ---- 4. 中等文本帧（126 ~ 65535 字节，测试 16 位扩展长度）----
        char[] midChars = new char[500];
        Arrays.fill(midChars, 'A');
        testText(client, "中等文本(500字节)", new String(midChars));

        // ---- 5. 大文本帧（> 65535 字节，测试 64 位扩展长度）----
        char[] largeChars = new char[70000];
        Arrays.fill(largeChars, 'B');
        testText(client, "大文本(70000字节)", new String(largeChars));

        // ---- 6. 短二进制帧（< 126 字节）----
        byte[] smallBin = new byte[64];
        for (int i = 0; i < smallBin.length; i++) smallBin[i] = (byte) (i & 0xFF);
        testBinary(client, "短二进制(64字节)", smallBin);

        // ---- 7. 中等二进制帧（126 ~ 65535 字节）----
        byte[] midBin = new byte[1000];
        for (int i = 0; i < midBin.length; i++) midBin[i] = (byte) (i % 256);
        testBinary(client, "中等二进制(1000字节)", midBin);

        // ---- 8. 大二进制帧（> 65535 字节）----
        byte[] largeBin = new byte[70000];
        for (int i = 0; i < largeBin.length; i++) largeBin[i] = (byte) (i % 256);
        testBinary(client, "大二进制(70000字节)", largeBin);

        // ---- 9. Ping / Pong 心跳 ----
        System.out.println("[发送] Ping 心跳帧");
        client.sendPing();
        Thread.sleep(500);

        // ---- 10. 连续快速发送（测试粘包/半包）----
        System.out.println("\n[测试] 连续快速发送 10 条消息（粘包场景）");
        int beforeCount = echoCount.get();
        for (int i = 0; i < 10; i++) {
            client.send("快速消息-" + i);
        }
        Thread.sleep(2000);
        int received = echoCount.get() - beforeCount;
        System.out.println("[结果] 发送 10 条, 收到回显 " + received + " 条"
                + (received == 10 ? " ✓" : " ✗ (期望 10)"));

        // ---- 11. Close 关闭帧 ----
        System.out.println("\n[发送] Close 关闭帧");
        client.closeBlocking();
        Thread.sleep(500);

        System.out.println("\n========== 测试完成, 共收到 " + echoCount.get() + " 条回显 ==========");
    }

    /**
     * 测试文本帧发送与回显验证
     */
    private static void testText(TestClient client, String label, String text) throws Exception {
        int before = echoCount.get();
        System.out.println("[发送] " + label + ", 长度=" + text.length());
        client.send(text);
        Thread.sleep(500);
        int received = echoCount.get() - before;
        System.out.println("  -> " + (received > 0 ? "收到回显 ✓" : "未收到回显 ✗"));
    }

    /**
     * 测试二进制帧发送与回显验证
     */
    private static void testBinary(TestClient client, String label, byte[] data) throws Exception {
        int before = echoCount.get();
        System.out.println("[发送] " + label);
        client.send(data);
        Thread.sleep(500);
        int received = echoCount.get() - before;
        System.out.println("  -> " + (received > 0 ? "收到回显 ✓" : "未收到回显 ✗"));
    }


    /**
     * 测试用 WebSocket 客户端实例
     */
    static class TestClient extends WebSocketClient {

        public TestClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("[客户端] 连接成功, HTTP状态=" + handshakedata.getHttpStatus());
        }

        @Override
        public void onMessage(String message) {
            int count = echoCount.incrementAndGet();
            System.out.println("[客户端] 收到文本回显 #" + count + ", 长度=" + message.length());
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            int count = echoCount.incrementAndGet();
            System.out.println("[客户端] 收到二进制回显 #" + count + ", 长度=" + bytes.remaining());
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("[客户端] 连接关闭, code=" + code + ", reason=" + reason
                    + ", remote=" + remote);
        }

        @Override
        public void onError(Exception ex) {
            System.out.println("[客户端] 错误: " + ex.getMessage());
            ex.printStackTrace();
        }

        @Override
        public void onWebsocketPing(WebSocket conn, Framedata f) {
            System.out.println("[客户端] 收到 Ping 帧");
            super.onWebsocketPing(conn, f);
        }

        @Override
        public void onWebsocketPong(WebSocket conn, Framedata f) {
            System.out.println("[客户端] 收到 Pong 帧 ✓");
        }
    }
}
