package com.gettyio.string.udp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;

/**
 * UDP 客户端业务处理器（简单示例）
 * <p>
 * 最基础的客户端 Handler 示例，仅打印收到的消息。
 * 综合测试场景请使用 {@link UdpClient}。
 * </p>
 */
public class ClientSimpleHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[UDP-Client] 通道已建立");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[UDP-Client] 通道已关闭");
    }

    @Override
    public void channelRead0(AbstractSocketChannel channel, DatagramPacket datagramPacket) {
        String content = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8);
        String sender = datagramPacket.getAddress().getHostAddress() + ":" + datagramPacket.getPort();
        System.out.println("[UDP-Client] 收到来自 " + sender + " 消息: " + content);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[UDP-Client] 异常: " + cause.getMessage());
    }
}
