package com.gettyio.string.udp;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;

public class ClientSimpleHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel socketChannel, DatagramPacket datagramPacket) {

        String address = datagramPacket.getAddress().getHostName() + ":" + datagramPacket.getPort();
        System.out.println("读取服务器" + address + "消息:" + new String(datagramPacket.getData()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("出错了");
    }
}
