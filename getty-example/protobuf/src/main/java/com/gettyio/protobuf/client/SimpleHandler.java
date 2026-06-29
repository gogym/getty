package com.gettyio.protobuf.client;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.protobuf.packet.MessageClass;

/**
 * Protobuf 客户端处理器 —— 打印服务端回显的消息详情。
 */
public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {

    /** 回显消息计数 */
    private static int echoCount = 0;

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[客户端] 连接服务端成功");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[客户端] 连接关闭");
    }

    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, MessageClass.Message msg) {
        echoCount++;
        System.out.println("[客户端] 收到回显 #" + echoCount);
        System.out.println("  id=" + msg.getId()
                + ", reqType=" + msg.getReqType()
                + ", serverId=" + msg.getServerId()
                + ", status=" + msg.getStatus());

        if (!msg.getBody().isEmpty()) {
            System.out.println("  body=" + msg.getBody()
                    + ", bodyType=" + msg.getBodyType()
                    + ", bodyLength=" + msg.getBodyLength());
        }
        if (!msg.getAck().isEmpty()) {
            System.out.println("  ack=" + msg.getAck());
        }
        if (!msg.getSenderId().isEmpty()) {
            System.out.println("  sender=" + msg.getSenderId()
                    + "(" + msg.getSenderName() + ")");
        }
        if (!msg.getReceiverId().isEmpty()) {
            System.out.println("  receiver=" + msg.getReceiverId()
                    + "(" + msg.getReceiverName() + ")");
        }
        if (!msg.getGroupId().isEmpty()) {
            System.out.println("  group=" + msg.getGroupId()
                    + "(" + msg.getGroupName() + ")");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[客户端] 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
