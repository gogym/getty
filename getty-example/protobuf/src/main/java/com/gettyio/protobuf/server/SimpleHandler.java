package com.gettyio.protobuf.server;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.group.DefaultChannelGroup;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.protobuf.packet.MessageClass;

public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {

    //实例化一个group保存客户端连接
    DefaultChannelGroup defaultChannelGroup = new DefaultChannelGroup();

    @Override
    public void channelAdded(SocketChannel aioChannel) {
        System.out.println("连接过来了");
        //把连接保存起来以备使用
        defaultChannelGroup.add(aioChannel);
        //可以通过AioChannel的channelId获取通道。比如与用户映射起来
        //AioChannel tempChannel = defaultChannelGroup.find(aioChannel.getChannelId());
        //tempChannel.writeAndFlush("123".getBytes());
    }

    @Override
    public void channelClosed(SocketChannel aioChannel) {
        System.out.println("服务器端连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, MessageClass.Message str) {
        System.out.println("读取消息:" + str.getBody());
    }

    @Override
    public void exceptionCaught(SocketChannel aioChannel, Throwable cause) {
        System.out.println("出错了");
    }

}
