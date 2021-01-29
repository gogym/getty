package com.gettyio.mqtt.server;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.expansion.handler.codec.mqtt.MqttMessage;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<MqttMessage> {
    @Override
    public void channelAdded(SocketChannel aioChannel) {
        System.out.println("mqtt客户端连接成功");
    }

    @Override
    public void channelClosed(SocketChannel aioChannel) {
        System.out.println("mqtt客户端连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, MqttMessage mqttMessage) {
        System.out.println("读取mqtt消息了:" + mqttMessage.toString());
    }

    @Override
    public void exceptionCaught(SocketChannel aioChannel, Throwable cause) {
        System.out.println("出错了");
        cause.printStackTrace();
    }
}
