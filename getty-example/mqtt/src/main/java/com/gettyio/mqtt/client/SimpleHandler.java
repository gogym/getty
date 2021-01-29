package com.gettyio.mqtt.client;


import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.expansion.handler.codec.mqtt.MqttMessage;
import com.gettyio.expansion.handler.codec.mqtt.MqttPublishMessage;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<MqttMessage> {
    @Override
    public void channelAdded(SocketChannel aioChannel) {

        System.out.println("连接服务器成功");

    }

    @Override
    public void channelClosed(SocketChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, MqttMessage mqttMessage) {
        System.out.println("读取消息了:" + mqttMessage.toString());

        switch (mqttMessage.fixedHeader().messageType()) {
            case PUBLISH:
                MqttPublishMessage mqttPublishMessage = (MqttPublishMessage) mqttMessage;
                AutoByteBuffer payload = mqttPublishMessage.payload();
                byte[] bytes = payload.array();
                System.out.println("payload：" + new String(bytes));
                break;
            default:
                break;
        }
    }

    @Override
    public void exceptionCaught(SocketChannel aioChannel, Throwable cause) {
        System.out.println("出错了");
        cause.printStackTrace();
    }
}
