package com.gettyio.protobuf.server;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.group.DefaultChannelGroup;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.protobuf.packet.MessageClass;

public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {

    /**
     * 实例化一个group保存客户端连接
     */
    DefaultChannelGroup defaultChannelGroup = new DefaultChannelGroup();

    int count = 0;

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("连接过来了");
        //把连接保存起来以备使用
        defaultChannelGroup.add(ctx.channel());
        //可以通过AioChannel的channelId获取通道。比如与用户映射起来
        //AioChannel tempChannel = defaultChannelGroup.find(aioChannel.getChannelId());
        //tempChannel.writeAndFlush("123".getBytes());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("服务器端连接关闭了");
    }


    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, MessageClass.Message str) {
        count++;
        System.out.println("消息:"+str.getBody()+"-----数量：" + count);
        final MessageClass.Message.Builder builder = MessageClass.Message.newBuilder();
        builder.setBody("123");
        aioChannel.writeAndFlush(builder.build());

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("出错了");
    }

}
