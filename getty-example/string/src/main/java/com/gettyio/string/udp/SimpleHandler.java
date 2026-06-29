package com.gettyio.string.udp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP 服务端业务处理器
 * <p>
 * 功能：
 * 1. 将收到的 UDP 数据报原样 Echo 回复给发送方
 * 2. 支持中文消息
 * </p>
 */
public class SimpleHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[UDP-Server] 通道已建立");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[UDP-Server] 通道已关闭");
    }

    /**
     * 收到 UDP 数据报后，Echo 回复给发送方
     *
     * @param channel        UDP 通道
     * @param datagramPacket 收到的数据报
     */
    @Override
    public void channelRead0(AbstractSocketChannel channel, DatagramPacket datagramPacket) throws Exception {
        // 提取发送方地址信息
        String senderAddress = datagramPacket.getAddress().getHostAddress();
        int senderPort = datagramPacket.getPort();
        String content = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8);

        System.out.println("[UDP-Server] 收到来自 " + senderAddress + ":" + senderPort + " 消息: " + content);

        // 构造回复消息
        String reply = "Echo: " + content;
        byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);
        DatagramPacket replyPacket = new DatagramPacket(replyBytes, replyBytes.length,
                new InetSocketAddress(senderAddress, senderPort));

        channel.writeAndFlush(replyPacket);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[UDP-Server] 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
