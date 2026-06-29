package com.gettyio.mqtt.server;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.mqtt.MqttMessage;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * MQTT 服务端处理器 —— 回显模式：收到什么消息就原样返回给客户端。
 */
public class SimpleHandler extends SimpleChannelInboundHandler<MqttMessage> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端连接成功");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端连接关闭");
    }

    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, MqttMessage mqttMessage) {
        System.out.println("[服务端] 收到消息: " + mqttMessage.fixedHeader().messageType());
        // 回显：收到什么就发回什么
        aioChannel.writeAndFlush(mqttMessage);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[服务端] 出错了");
        cause.printStackTrace();
    }
}
