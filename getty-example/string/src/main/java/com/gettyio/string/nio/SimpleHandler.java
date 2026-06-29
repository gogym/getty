package com.gettyio.string.nio;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * NIO 服务端业务处理器
 * <p>
 * 功能：
 * 1. 连接建立/关闭日志
 * 2. 将收到的消息原样 Echo 回复（支持中文）
 * 3. 统计已处理的消息数量
 * </p>
 */
public class SimpleHandler extends SimpleChannelInboundHandler<String> {

    /** 当前连接累计收到的消息数 */
    private int messageCount = 0;

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[NIO-Server] 客户端连接建立");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[NIO-Server] 客户端连接关闭，累计收到 " + messageCount + " 条消息");
    }

    /**
     * 收到客户端消息后，Echo 回复
     *
     * @param channel 客户端通道
     * @param msg     收到的字符串消息（已由 StringDecoder 解码，去掉了分隔符）
     */
    @Override
    public void channelRead0(AbstractSocketChannel channel, String msg) {
        messageCount++;
        System.out.println("[NIO-Server] 收到: " + msg);
        // Echo 回复，末尾加 "\r\n" 作为分隔符
        channel.writeAndFlush("Echo: " + msg + "\r\n");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[NIO-Server] 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
