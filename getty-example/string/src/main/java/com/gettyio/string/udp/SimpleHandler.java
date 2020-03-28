package com.gettyio.string.udp;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.net.DatagramPacket;

public class SimpleHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    public void channelAdded(SocketChannel aioChannel) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(SocketChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, DatagramPacket datagramPacket) {

        System.out.println("读取消息了:" + new String(datagramPacket.getData()));
        System.out.println("客户端地址:" + datagramPacket.getAddress().getHostName() + ":" + datagramPacket.getPort());
    }

    @Override
    public void exceptionCaught(SocketChannel aioChannel, Throwable cause) {
        System.out.println("出错了");
    }
}
