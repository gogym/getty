package com.gettyio.protobuf.client;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.protobuf.packet.MessageClass;

public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("客户端连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, MessageClass.Message str) {
        System.out.println("服务返回消息了:" + str.getBody());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("出错了");
        cause.printStackTrace();
    }
}
