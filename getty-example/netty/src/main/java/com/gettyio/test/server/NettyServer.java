package com.gettyio.test.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyServer {

    public static void main(String[] args) {


        // 启动新的线程托管服务
        new Thread() {
            @Override
            public void run() {

                EventLoopGroup bossGroup = new NioEventLoopGroup();// boss线程池
                EventLoopGroup workerGroup = new NioEventLoopGroup(
                        4);// worker线程池
                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            //.handler(new LoggingHandler(LogLevel.INFO))
                            // 使用TCP
                            .childHandler(new GimServerInitializer())
                            // 初始化配置的处理器
                            .option(ChannelOption.SO_BACKLOG, 128)
                            // BACKLOG用于构造服务端套接字ServerSocket对象，标识当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度。如果未设置或所设置的值小于1，Java将使用默认值50。
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            // 是否启用心跳保活机制。在双方TCP套接字建立连接后（即都进入ESTABLISHED状态）并且在两个小时左右上层没有任何数据传输的情况下，这套机制才会被激活。
                            .childOption(
                                    ChannelOption.WRITE_BUFFER_WATER_MARK,
                                    new WriteBufferWaterMark(
                                            32 * 1024,
                                            256 * 1024));// 配置水位线
                    // 绑定端口，开始接收进来的连接
                    ChannelFuture f = b.bind(3333).sync();
                    System.out.println("[gim已启动，等待连接]");

                    // 等待服务器 socket 关闭 ,这不会发生，可以优雅地关闭服务器。
                    f.channel().closeFuture().sync();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                }
            }
        }.start();


    }
}
