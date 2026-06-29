package com.gettyio.string.nio;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * NIO 客户端业务处理器（简单示例）
 * <p>
 * 最基础的客户端 Handler 示例，仅打印收到的消息。
 * 综合测试场景请使用 {@link NioClient}。
 * </p>
 */
public class ClientSimpleHandler extends SimpleChannelInboundHandler<String> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[NIO-Client] 连接建立");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[NIO-Client] 连接关闭");
    }

    @Override
    public void channelRead0(AbstractSocketChannel channel, String msg) {
        System.out.println("[NIO-Client] 收到: " + msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[NIO-Client] 异常: " + cause.getMessage());
    }
}
