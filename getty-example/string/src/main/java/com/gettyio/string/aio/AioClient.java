package com.gettyio.string.aio;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.expansion.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.expansion.handler.codec.string.StringDecoder;
import com.gettyio.expansion.handler.codec.string.StringEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AIO TCP 综合测试客户端
 * <p>
 * 包含 5 个测试场景，覆盖基本收发、中文、大包、多客户端并发、批量吞吐。
 * 需要先启动 {@link AioServer}，再运行本类。
 * </p>
 *
 * <pre>
 * 场景1：基本收发   - 发送英文消息，验证 Echo 回复
 * 场景2：中文消息   - 发送中文消息，验证 UTF-8 编解码
 * 场景3：大包测试   - 发送 10KB 消息，验证分片/组帧
 * 场景4：多客户端   - 5 个客户端同时连接并通信
 * 场景5：批量吞吐   - 单连接发送 10 万条消息，统计吞吐量
 * </pre>
 */
public class AioClient {

    /** 服务器地址 */
    private static final String HOST = "127.0.0.1";
    /** 服务器端口 */
    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        System.out.println("========== AIO TCP 综合测试 ==========\n");

        test1_BasicEcho();
        test2_ChineseMessage();
        test3_LargePacket();
        test4_MultiClient();
        test5_BatchThroughput();

        System.out.println("\n========== 全部测试完成 ==========");
    }

    // ======================== 场景1：基本收发 ========================

    /**
     * 场景1：基本收发
     * 发送一条英文消息，等待服务端 Echo 回复，验证内容正确。
     */
    private static void test1_BasicEcho() throws Exception {
        System.out.println("--- 场景1：基本收发 ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        AioClientStarter client = createClient(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, String msg) {
                received[0] = msg;
                latch.countDown();
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                channel.writeAndFlush("Hello, Getty AIO!\r\n");
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        if (completed && received[0] != null) {
            System.out.println("  收到回复: " + received[0]);
            System.out.println("  结果: " + ("Echo: Hello, Getty AIO!".equals(received[0]) ? "PASS ✓" : "FAIL ✗"));
        } else {
            System.out.println("  结果: FAIL ✗ (超时)");
        }
        System.out.println();
        Thread.sleep(500);
    }

    // ======================== 场景2：中文消息 ========================

    /**
     * 场景2：中文消息
     * 发送包含中文的消息，验证 UTF-8 编解码不会乱码或丢字节。
     */
    private static void test2_ChineseMessage() throws Exception {
        System.out.println("--- 场景2：中文消息 ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        AioClientStarter client = createClient(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, String msg) {
                received[0] = msg;
                latch.countDown();
            }
        });

        String chineseMsg = "你好，Getty框架！支持中文消息测试。\r\n";
        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                channel.writeAndFlush(chineseMsg);
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        if (completed && received[0] != null) {
            System.out.println("  发送: " + chineseMsg.trim());
            System.out.println("  收到: " + received[0]);
            String expected = "Echo: " + chineseMsg.trim();
            System.out.println("  结果: " + (expected.equals(received[0]) ? "PASS ✓" : "FAIL ✗"));
        } else {
            System.out.println("  结果: FAIL ✗ (超时)");
        }
        System.out.println();
        Thread.sleep(500);
    }

    // ======================== 场景3：大包测试 ========================

    /**
     * 场景3：大包测试
     * 发送 10KB 的大消息，验证框架的分片/组帧能力。
     */
    private static void test3_LargePacket() throws Exception {
        System.out.println("--- 场景3：大包测试（10KB） ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        AioClientStarter client = createClient(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, String msg) {
                received[0] = msg;
                latch.countDown();
            }
        });

        // 构造 10KB 的消息体
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("0123456789");  // 每轮 10 字符，1000 轮 = 10000 字符 ≈ 10KB
        }
        String largeMsg = sb.toString();

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                channel.writeAndFlush(largeMsg + "\r\n");
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        if (completed && received[0] != null) {
            String expected = "Echo: " + largeMsg;
            boolean match = expected.equals(received[0]);
            System.out.println("  发送大小: " + largeMsg.length() + " 字符");
            System.out.println("  收到大小: " + received[0].length() + " 字符");
            System.out.println("  结果: " + (match ? "PASS ✓" : "FAIL ✗ (内容不匹配)"));
        } else {
            System.out.println("  结果: FAIL ✗ (超时)");
        }
        System.out.println();
        Thread.sleep(500);
    }

    // ======================== 场景4：多客户端并发 ========================

    /**
     * 场景4：多客户端并发
     * 同时启动 5 个客户端连接，每个发送一条消息，验证全部收到正确回复。
     */
    private static void test4_MultiClient() throws Exception {
        System.out.println("--- 场景4：多客户端并发（5个） ---");

        int clientCount = 5;
        CountDownLatch startLatch = new CountDownLatch(clientCount);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        String[] results = new String[clientCount];

        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            AioClientStarter client = createClient(new SimpleChannelInboundHandler<String>() {
                @Override
                public void channelRead0(AbstractSocketChannel channel, String msg) {
                    results[index] = msg;
                    doneLatch.countDown();
                }
            });

            client.start(new ConnectHandler() {
                @Override
                public void onCompleted(AbstractSocketChannel channel) {
                    startLatch.countDown();
                    channel.writeAndFlush("Client-" + index + " 发来消息\r\n");
                }

                @Override
                public void onFailed(Throwable exc) {
                    startLatch.countDown();
                    doneLatch.countDown();
                }
            });
        }

        boolean allDone = doneLatch.await(10, TimeUnit.SECONDS);
        int passCount = 0;
        for (int i = 0; i < clientCount; i++) {
            if (results[i] != null && results[i].equals("Echo: Client-" + i + " 发来消息")) {
                passCount++;
            }
        }
        System.out.println("  成功回复: " + passCount + "/" + clientCount);
        System.out.println("  结果: " + (allDone && passCount == clientCount ? "PASS ✓" : "FAIL ✗"));
        System.out.println();
        Thread.sleep(500);
    }

    // ======================== 场景5：批量吞吐测试 ========================

    /**
     * 场景5：批量吞吐测试
     * 单连接发送 10 万条消息，统计总耗时和吞吐量。
     * 不等待回复（fire-and-forget），纯测发送性能。
     */
    private static void test5_BatchThroughput() throws Exception {
        System.out.println("--- 场景5：批量吞吐测试（10万条） ---");

        int totalMessages = 100000;
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AbstractSocketChannel[] channelRef = {null};

        AioClientStarter client = createClient(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, String msg) {
                // 批量测试不处理回复
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                channelRef[0] = channel;
                connectLatch.countDown();
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                connectLatch.countDown();
            }
        });

        connectLatch.await(5, TimeUnit.SECONDS);
        if (channelRef[0] == null) {
            System.out.println("  结果: FAIL ✗ (无法连接)");
            return;
        }

        // 开始批量发送
        String msg = " throughput-test-msg \r\n";
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalMessages; i++) {
            channelRef[0].writeAndFlush(msg);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double tps = totalMessages * 1000.0 / Math.max(elapsed, 1);

        System.out.println("  发送消息数: " + totalMessages);
        System.out.println("  总耗时: " + elapsed + " ms");
        System.out.println("  吞吐量: " + String.format("%.0f", tps) + " msg/s");
        System.out.println("  结果: PASS ✓ (完成发送)");
        System.out.println();
    }

    // ======================== 工具方法 ========================

    /**
     * 创建一个配置好的 AIO 客户端（不含 SSL）
     *
     * @param businessHandler 业务处理器，用于接收服务端回复
     * @return 配置完成的 AioClientStarter 实例
     */
    private static AioClientStarter createClient(SimpleChannelInboundHandler<String> businessHandler) {
        GettyConfig config = new GettyConfig();
        config.setHost(HOST);
        config.setPort(PORT);

        AioClientStarter client = new AioClientStarter(config);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.getChannelPipeline();
                pipeline.addLast(new StringEncoder());
                pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
                pipeline.addLast(new StringDecoder());
                pipeline.addLast(businessHandler);
            }
        });
        return client;
    }
}
