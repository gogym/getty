package com.gettyio.string.aio;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.expansion.handler.codec.string.StringDecoder;
import com.gettyio.expansion.handler.codec.string.StringEncoder;
import com.gettyio.expansion.handler.timeout.ReConnectHandler;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class AioClient {

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

        int i = 0;
        while (i < 3) {
            AioClient ac = new AioClient();
            ac.test(8888);
            i++;
        }
    }


    private void test(int port) {


        ClientConfig aioConfig = new ClientConfig();
        aioConfig.setHost("127.0.0.1");
        aioConfig.setPort(port);
        aioConfig.setFlowControl(false);
        aioConfig.setHighWaterMark(2);
        aioConfig.setLowWaterMark(1);


        final AioClientStarter client = new AioClientStarter(aioConfig);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                //责任链
                ChannelPipeline defaultChannelPipeline = channel.getChannelPipeline();

                //获取证书
                String pkPath = getClass().getClassLoader().getResource("clientStore.jks").getPath();
                //ssl配置
                SSLConfig sSLConfig = new SSLConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                //sSLConfig.setTrustFile(pkPath);
                //sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(true);
                //初始化ssl服务
                //defaultChannelPipeline.addFirst(new SSLHandler(sSLConfig));

                defaultChannelPipeline.addLast(new ReConnectHandler(new ConnectHandler() {
                    @Override
                    public void onCompleted(AbstractSocketChannel channel) {
                        System.out.println("重连成功");
                    }

                    @Override
                    public void onFailed(Throwable exc) {
                        System.out.println("重连失败");
                    }
                }));

                defaultChannelPipeline.addLast(new StringEncoder());
                //指定结束符解码器
                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
                //字符串解码器
                defaultChannelPipeline.addLast(new StringDecoder());
                //定义消息解码器
                defaultChannelPipeline.addLast(new ClientSimpleHandler());
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(final AbstractSocketChannel abstractSocketChannel) {

                Thread bizThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long ct = System.currentTimeMillis();
                        int i = 0;
                        try {
                            String s = "12\r\n";
                            byte[] msgBody = s.getBytes();

                            while (i < 1000000) {
                                long before = System.nanoTime();
                                boolean flag = abstractSocketChannel.writeAndFlush(msgBody);
                                long elapsed = System.nanoTime() - before;
                                i++;
                                if (i % 1000 == 0) {
                                    Thread.yield();
                                }
//                                if (i % 100 == 0) {
//                                    System.out.println("[biz] 已发送 " + i + " 条, 单次耗时: " + elapsed / 1000 + "us, 通道状态: " + abstractSocketChannel.isInvalid());
//                                }
//                                // 如果单次 writeAndFlush 超过 100ms，打印告警
//                                if (elapsed > 100_000_000L) {
//                                    System.out.println("[biz] 警告: 第 " + i + " 次 writeAndFlush 耗时 " + elapsed / 1_000_000 + "ms，疑似阻塞！");
//                                }
                            }

                        } catch (Throwable e) {
                            System.err.println("Thread-0 异常退出，已发送: " + i);
                            e.printStackTrace();
                        } finally {
                            long lt = System.currentTimeMillis();
                            System.out.println("总耗时(ms)：" + (lt - ct));
                            System.out.println("发送消息数量：" + i + "条");
                            System.out.flush();
                        }
                    }
                });
                bizThread.start();

                // 监控线程：如果业务线程 10 秒内未完成，dump 线程栈
                final Thread bizRef = bizThread;
                Thread monitor = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            bizRef.join(30_000);
                            if (bizRef.isAlive()) {
                                System.err.println("[monitor] 业务线程 10 秒未完成！线程状态: " + bizRef.getState());
                                System.err.println("[monitor] 业务线程栈:");
                                for (StackTraceElement ste : bizRef.getStackTrace()) {
                                    System.err.println("  at " + ste);
                                }
                                System.err.println("[monitor] 所有线程:");
                                for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                                    Thread t = entry.getKey();
                                    System.err.println("  [" + t.getName() + "] state=" + t.getState());
                                    for (StackTraceElement ste : entry.getValue()) {
                                        System.err.println("    at " + ste);
                                    }
                                }
                                System.err.flush();
                            }
                        } catch (InterruptedException ignored) {}
                    }
                }, "biz-monitor");
                monitor.setDaemon(true);
                monitor.start();
            }

            @Override
            public void onFailed(Throwable exc) {
                exc.printStackTrace();
            }
        });


    }


}
