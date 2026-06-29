package com.gettyio.protobuf.server;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.group.DefaultChannelGroup;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.protobuf.packet.MessageClass;

/**
 * Protobuf 服务端处理器 —— 模拟 IM 业务回显。
 * <p>
 * 根据 reqType 区分消息类型，构建对应的回显响应。
 * </p>
 */
public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {

    /** 保存所有客户端连接 */
    private final DefaultChannelGroup channelGroup = new DefaultChannelGroup();

    /** 消息计数 */
    private int msgCount = 0;

    /** 请求类型常量 */
    private static final int REQ_CONNECT = 1;     // 连接请求
    private static final int REQ_HEARTBEAT = 2;   // 心跳
    private static final int REQ_SINGLE = 10;     // 单聊
    private static final int REQ_GROUP = 11;      // 群聊
    private static final int REQ_ACK = 20;        // ACK确认

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端连接, channelId=" + ctx.channel().getChannelId());
        channelGroup.add(ctx.channel());
        System.out.println("[服务端] 当前在线连接数: " + channelGroup.size());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端断开, channelId=" + ctx.channel().getChannelId());
        System.out.println("[服务端] 当前在线连接数: " + channelGroup.size());
    }

    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, MessageClass.Message msg) {
        msgCount++;
        int reqType = msg.getReqType();
        System.out.println("[服务端] 收到消息 #" + msgCount
                + " reqType=" + reqType + " id=" + msg.getId());

        MessageClass.Message.Builder resp = MessageClass.Message.newBuilder();
        resp.setId(msg.getId());
        resp.setMsgTime(System.currentTimeMillis());
        resp.setServerId("server-001");

        switch (reqType) {
            case REQ_CONNECT:
                // 连接请求 → 回复欢迎消息
                resp.setReqType(REQ_CONNECT);
                resp.setAck("connect_ok");
                resp.setBody("欢迎连接, senderId=" + msg.getSenderId());
                resp.setStatus(0);
                System.out.println("  -> 处理连接请求: senderId=" + msg.getSenderId());
                break;

            case REQ_HEARTBEAT:
                // 心跳 → 回复心跳ACK
                resp.setReqType(REQ_HEARTBEAT);
                resp.setAck("pong");
                resp.setStatus(0);
                System.out.println("  -> 心跳回复");
                break;

            case REQ_SINGLE:
                // 单聊 → 回显消息内容，交换发送/接收者
                resp.setReqType(REQ_SINGLE);
                resp.setSenderId(msg.getReceiverId());
                resp.setSenderName("服务端");
                resp.setReceiverId(msg.getSenderId());
                resp.setReceiverName(msg.getSenderName());
                resp.setBody("[回显] " + msg.getBody());
                resp.setBodyType(msg.getBodyType());
                resp.setBodyLength(msg.getBody().length());
                resp.setAck(msg.getId());
                resp.setStatus(0);
                System.out.println("  -> 单聊回显: " + msg.getSenderName()
                        + " -> " + msg.getReceiverName() + ", body=" + msg.getBody());
                break;

            case REQ_GROUP:
                // 群聊 → 回显消息，广播给所有连接
                resp.setReqType(REQ_GROUP);
                resp.setSenderId(msg.getSenderId());
                resp.setSenderName(msg.getSenderName());
                resp.setGroupId(msg.getGroupId());
                resp.setGroupName(msg.getGroupName());
                resp.setBody("[群回显] " + msg.getBody());
                resp.setBodyType(msg.getBodyType());
                resp.setBodyLength(msg.getBody().length());
                resp.setAck(msg.getId());
                resp.setStatus(0);
                System.out.println("  -> 群聊回显: group=" + msg.getGroupName()
                        + ", body=" + msg.getBody());
                // 广播给所有连接
                channelGroup.writeToAll(resp.build());
                return;

            case REQ_ACK:
                // ACK确认 → 回复确认结果
                resp.setReqType(REQ_ACK);
                resp.setAck(msg.getAck());
                resp.setResult(0);
                resp.setStatus(0);
                System.out.println("  -> ACK确认: ack=" + msg.getAck());
                break;

            default:
                // 未知类型 → 原样回显
                resp.setReqType(reqType);
                resp.setBody("[未知类型] " + msg.getBody());
                resp.setStatus(1);
                System.out.println("  -> 未知reqType=" + reqType + ", 原样回显");
                break;
        }

        aioChannel.writeAndFlush(resp.build());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[服务端] 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
