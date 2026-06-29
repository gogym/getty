package com.gettyio.string.udp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.channel.starter.NioClientStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketDecoder;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketEncoder;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP 综合测试客户端
 * <p>
 * 包含 5 个测试场景，覆盖基本收发、中文、大包、多线程并发、批量吞吐。
 * 需要先启动 {@link UdpServer}，再运行本类。
 * </p>
 * <p>
 * 注意：UDP 是无连接协议，不保证送达和顺序。大包可能因 MTU 限制被分片。
 * </p>
 *
 * <pre>
 * 场景1：基本收发   - 发送英文 UDP 数据报，验证 Echo 回复
 * 场景2：中文消息   - 发送中文消息，验证 UTF-8 编解码
 * 场景3：大包测试   - 发送 1KB 消息，验证分片组帧（UDP 安全上限约 60KB）
 * 场景4：多线程并发 - 3 个线程同时发送，验证全部收到回复
 * 场景5：批量吞吐   - 快速发送 1 万条消息，统计吞吐量
 * </pre>
 */
public class UdpClient {

    /** 服务器地址 */
    private static final String HOST = "127.0.0.1";
    /** 服务器端口 */
    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        System.out.println("========== UDP 综合测试 ==========\n");

        test1_BasicEcho();
        test2_ChineseMessage();
        test3_LargePacket();
        test4_MultiThreadSend();
        test5_BatchThroughput();

        System.out.println("\n========== 全部测试完成 ==========");
    }

    // ======================== 场景1：基本收发 ========================

    /**
     * 场景1：基本收发
     * 发送一条英文 UDP 数据报，等待服务端 Echo 回复。
     */
    private static void test1_BasicEcho() throws Exception {
        System.out.println("--- 场景1：基本收发 ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        NioClientStarter client = createClient(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
                received[0] = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                latch.countDown();
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                try {
                    String msg = "Hello, Getty UDP!";
                    byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                            new InetSocketAddress(HOST, PORT));
                    channel.writeAndFlush(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                    latch.countDown();
                }
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
            System.out.println("  结果: " + ("Echo: Hello, Getty UDP!".equals(received[0]) ? "PASS ✓" : "FAIL ✗"));
        } else {
            System.out.println("  结果: FAIL ✗ (超时)");
        }
        System.out.println();
        Thread.sleep(300);
    }

    // ======================== 场景2：中文消息 ========================

    /**
     * 场景2：中文消息
     * 发送包含中文的 UDP 数据报，验证 UTF-8 编解码。
     */
    private static void test2_ChineseMessage() throws Exception {
        System.out.println("--- 场景2：中文消息 ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        NioClientStarter client = createClient(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
                received[0] = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                latch.countDown();
            }
        });

        String chineseMsg = "你好，Getty UDP 中文测试！";
        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                try {
                    byte[] bytes = chineseMsg.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                            new InetSocketAddress(HOST, PORT));
                    channel.writeAndFlush(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        if (completed && received[0] != null) {
            System.out.println("  发送: " + chineseMsg);
            System.out.println("  收到: " + received[0]);
            String expected = "Echo: " + chineseMsg;
            System.out.println("  结果: " + (expected.equals(received[0]) ? "PASS ✓" : "FAIL ✗"));
        } else {
            System.out.println("  结果: FAIL ✗ (超时)");
        }
        System.out.println();
        Thread.sleep(300);
    }

    // ======================== 场景3：大包测试 ========================

    /**
     * 场景3：大包测试
     * 发送 1KB 的 UDP 数据报（UDP 安全上限约 60KB，此处测试分片组帧）。
     */
    private static void test3_LargePacket() throws Exception {
        System.out.println("--- 场景3：大包测试（1KB） ---");

        CountDownLatch latch = new CountDownLatch(1);
        String[] received = {null};

        NioClientStarter client = createClient(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
                received[0] = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                latch.countDown();
            }
        });

        // 构造 1KB 的消息体
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("0123456789");  // 每轮 10 字符，100 轮 = 1000 字符 ≈ 1KB
        }
        String largeMsg = sb.toString();

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                try {
                    byte[] bytes = largeMsg.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                            new InetSocketAddress(HOST, PORT));
                    channel.writeAndFlush(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
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
        Thread.sleep(300);
    }

    // ======================== 场景4：多线程并发发送 ========================

    /**
     * 场景4：多线程并发发送
     * 3 个线程同时发送 UDP 数据报，验证全部收到回复。
     */
    private static void test4_MultiThreadSend() throws Exception {
        System.out.println("--- 场景4：多线程并发发送（3个线程） ---");

        int threadCount = 3;
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        NioClientStarter client = createClient(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
                String reply = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (reply.startsWith("Echo:")) {
                    successCount.incrementAndGet();
                }
                doneLatch.countDown();
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(AbstractSocketChannel channel) {
                // 启动多个线程同时发送
                for (int i = 0; i < threadCount; i++) {
                    final int index = i;
                    new Thread(() -> {
                        try {
                            String msg = "Thread-" + index + " 发来消息";
                            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                                    new InetSocketAddress(HOST, PORT));
                            channel.writeAndFlush(packet);
                        } catch (Exception e) {
                            e.printStackTrace();
                            doneLatch.countDown();
                        }
                    }, "udp-sender-" + i).start();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.err.println("  连接失败: " + exc.getMessage());
                for (int i = 0; i < threadCount; i++) {
                    doneLatch.countDown();
                }
            }
        });

        boolean allDone = doneLatch.await(10, TimeUnit.SECONDS);
        int success = successCount.get();
        System.out.println("  成功回复: " + success + "/" + threadCount);
        System.out.println("  结果: " + (allDone && success == threadCount ? "PASS ✓" : "FAIL ✗"));
        System.out.println();
        Thread.sleep(300);
    }

    // ======================== 场景5：批量吞吐测试 ========================

    /**
     * 场景5：批量吞吐测试
     * 快速发送 1 万条 UDP 数据报，统计吞吐量。
     * 注意：UDP 不保证送达，实际收到数量可能少于发送数量。
     */
    private static void test5_BatchThroughput() throws Exception {
        System.out.println("--- 场景5：批量吞吐测试（1万条） ---");

        int totalMessages = 10000;
        CountDownLatch connectLatch = new CountDownLatch(1);
        AbstractSocketChannel[] channelRef = {null};

        NioClientStarter client = createClient(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
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
        String msg = "udp-throughput-test";
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalMessages; i++) {
            DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length,
                    new InetSocketAddress(HOST, PORT));
            channelRef[0].writeAndFlush(packet);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double tps = totalMessages * 1000.0 / Math.max(elapsed, 1);

        System.out.println("  发送消息数: " + totalMessages);
        System.out.println("  总耗时: " + elapsed + " ms");
        System.out.println("  吞吐量: " + String.format("%.0f", tps) + " msg/s");
        System.out.println("  结果: PASS ✓ (完成发送，UDP 不保证送达)");
        System.out.println();
    }

    // ======================== 工具方法 ========================

    /**
     * 创建一个配置好的 UDP 客户端
     *
     * @param businessHandler 业务处理器，用于接收服务端回复
     * @return 配置完成的 NioClientStarter 实例（UDP 模式）
     */
    private static NioClientStarter createClient(SimpleChannelInboundHandler<DatagramPacket> businessHandler) {
        GettyConfig config = new GettyConfig();
        config.setHost(HOST);
        config.setPort(PORT);

        NioClientStarter client = new NioClientStarter(config);
        client.socketMode(SocketMode.UDP).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.getChannelPipeline();
                pipeline.addLast(new DatagramPacketEncoder());
                pipeline.addLast(new DatagramPacketDecoder());
                pipeline.addLast(businessHandler);
            }
        });
        return client;
    }
}
