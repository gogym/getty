package org.getty.test.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.getty.test.packet.MessageClass;
import org.getty.test.server.GimServerInitializer;

public class NettyClient {

    public static void main(String[] args) {

        // 启动新的线程托管服务
        new Thread() {
            @Override
            public void run() {

                ChannelFuture future;//信道
                EventLoopGroup group = new NioEventLoopGroup(2);
                Bootstrap bootstrap = new Bootstrap();

                bootstrap.group(group).channel(NioSocketChannel.class)//设置channel类型
                        .option(ChannelOption.TCP_NODELAY, true)//在TCP/IP协议中，无论发送多少数据，总是要在数据前面加上协议头，同时，对方接收到数据，也需要发送ACK表示确认。为了尽可能的利用网络带宽，TCP总是希望尽可能的发送足够大的数据。这里就涉及到一个名为Nagle的算法，该算法的目的就是为了尽可能发送大块数据，避免网络中充斥着许多小数据块。 TCP_NODELAY就是用于启用或关于Nagle算法。如果要求高实时性，有数据发送时就马上发送，就将该选项设置为true关闭Nagle算法；如果要减少发送次数减少网络交互，就设置为false等累积一定大小后再发送。默认为false。
                        .option(ChannelOption.SO_KEEPALIVE, true)//是否启用心跳保活机制。在双方TCP套接字建立连接后（即都进入ESTABLISHED状态）并且在两个小时左右上层没有任何数据传输的情况下，这套机制才会被激活。
                        .option(ChannelOption.SO_REUSEADDR, true)//这个套接字选项通知内核，如果端口忙，但TCP状态位于 TIME_WAIT ，可以重用端口。如果端口忙，而TCP状态位于其他状态，重用端口时依旧得到一个错误信息，指明"地址已经使用中"。如果你的服务程序停止后想立即重启，而新套接字依旧使用同一端口，此时 SO_REUSEADDR 选项非常有用。必须意识到，此时任何非期望数据到达，都可能导致服务程序反应混乱，不过这只是一种可能，事实上很不可能
                        .option(EpollChannelOption.SO_REUSEPORT, true)//SO_REUSEPORT支持多个进程或者线程绑定到同一端口，提高服务器程序的性能
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).handler(new GimClientInitializer());//连接超时时间

                future = bootstrap.connect("127.0.0.1", 3333);

                // 添加future监听
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture f) throws Exception {
                        boolean succeed = f.isSuccess();
                        // 如果重连失败，则调用ChannelInactive方法，再次出发重连事件，一直尝试12次，如果失败则不再重连
                        if (!succeed) {
                        } else {
                            System.out.println("连接成功");
                            Channel c = f.channel();

                            MessageClass.Message.Builder builder = MessageClass.Message.newBuilder();
                            builder.setId("123");

                            for (int i = 0; i < 100; i++) {
                                c.writeAndFlush(builder.build());
                            }


//                            String s = "me\r\n";
//                            byte[] msgBody = s.getBytes("utf-8");
//                            long ct = System.currentTimeMillis();
//                            for (int i = 0; i < 1000000; i++) {
//                                c.writeAndFlush(msgBody);
//                            }
//
//                            long lt = System.currentTimeMillis();
//                            System.out.println("耗时：" + (lt - ct));


                        }
                    }
                });


            }
        }.start();

    }
}
