package com.gettyio.string.udp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class SimpleHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AbstractSocketChannel abstractSocketChannel, DatagramPacket datagramPacket) throws Exception{

        String address = datagramPacket.getAddress().getHostName() + ":" + datagramPacket.getPort();
        System.out.println("读取客户端" + address + "消息:" + new String(datagramPacket.getData()));

        String msg = "你发的消息是：" + new String(datagramPacket.getData());
        byte[] msgBody = msg.getBytes(StandardCharsets.UTF_8);
        final DatagramPacket dd = new DatagramPacket(msgBody, msgBody.length, new InetSocketAddress(datagramPacket.getAddress().getHostAddress(), datagramPacket.getPort()));
        final long ct = System.currentTimeMillis();
        abstractSocketChannel.writeAndFlush(dd);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("出错了");
    }
}
