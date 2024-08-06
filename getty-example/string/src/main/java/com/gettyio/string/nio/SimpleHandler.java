package com.gettyio.string.nio;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<String> {


    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
            System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AbstractSocketChannel abstractSocketChannel, String str) {
        //System.out.println("读取客户端的消息:" + str);
        //abstractSocketChannel.writeAndFlush("你发的消息是：" + str + "\r\n");

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("出错了");
    }
}
