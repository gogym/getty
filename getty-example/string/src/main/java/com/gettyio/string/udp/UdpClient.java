package com.gettyio.string.udp;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.channel.starter.NioClientStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.thread.ThreadPool;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketDecoder;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketEncoder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class UdpClient {

    static ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {


        int i = 0;
        while (i < 1) {

            test(8888);
            i++;
        }
    }


    private static void test(int port) {

        ClientConfig aioConfig = new ClientConfig();
        aioConfig.setHost("127.0.0.1");
        aioConfig.setPort(port);
        //aioConfig.setBufferWriterQueueSize(2 * 1024 * 1024);


        NioClientStarter client = new NioClientStarter(aioConfig);
        client.socketMode(SocketMode.UDP).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                //责任链
                ChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                defaultChannelPipeline.addLast(new DatagramPacketEncoder());
                defaultChannelPipeline.addLast(new DatagramPacketDecoder());
                //定义消息解码器
                defaultChannelPipeline.addLast(new ClientSimpleHandler());
            }
        });


        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(final SocketChannel channel) {
                try {
                    String s = "12";
                    byte[] msgBody = s.getBytes(StandardCharsets.UTF_8);
                    final DatagramPacket datagramPacket = new DatagramPacket(msgBody, msgBody.length, new InetSocketAddress("127.0.0.1", 8888));
                    final long ct = System.currentTimeMillis();

                    for (int j = 0; j < 1; j++) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int i = 0;
                                for (; i < 100; i++) {
                                    channel.writeAndFlush(datagramPacket);
                                }
                                long lt = System.currentTimeMillis();
                                System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");
                                System.out.printf("发送消息数量：" + i + "条\r\n");
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {

            }
        });


    }

}
