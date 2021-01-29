package com.gettyio.mqtt.client;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.expansion.handler.codec.mqtt.*;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * mqtt客户端
 */
public class MqttClient {


    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

        ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);
        //尝试链接多个客户端
        int i = 0;
        while (i < 1) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    test(9999);
                }
            });
            i++;
        }
    }


    private static void test(int port) {

        AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                //责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();
                //添加mqtt编解码器
                defaultChannelPipeline.addLast(MqttEncoder.INSTANCE);
                defaultChannelPipeline.addLast(new MqttDecoder());

                //定义消息解码器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(final SocketChannel channel) {
                try {

                    //mqtt连接消息
//                    MqttConnectMessage mqttMessage = new MqttConnectMessage(
//                            new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
//                            new MqttConnectVariableHeader("MQTT", 4, true, true, true, 0, true, true, 20),
//                            new MqttConnectPayload("123", "willtopic", "willmessage", "username", "password"));
                    //mqtt连接ack消息
//                    MqttConnAckMessage mqttMessage = new MqttConnAckMessage(
//                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
//                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false));

//mqtt发布消息
//                    MqttPublishMessage mqttMessage = new MqttPublishMessage(
//                            new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0),
//                            new MqttPublishVariableHeader("MQTT", 4),
//                            AutoByteBuffer.newByteBuffer().writeBytes("你好".getBytes()));
//mqtt订阅消息
//                    List<MqttTopicSubscription> topicSubscriptions=new ArrayList<>();
//                    MqttTopicSubscription mqttTopicSubscription=new MqttTopicSubscription("aaa",MqttQoS.AT_MOST_ONCE);
//                    topicSubscriptions.add(mqttTopicSubscription);
//                    MqttSubscribeMessage mqttMessage = new MqttSubscribeMessage(
//                            new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
//                            MqttMessageIdVariableHeader.from(4),
//                           new MqttSubscribePayload(topicSubscriptions));

                    //mqtt取消订阅消息
//                    List<String> topicSubscriptions=new ArrayList<>();
//                    topicSubscriptions.add("aaa");
//                    MqttUnsubscribeMessage mqttMessage = new MqttUnsubscribeMessage(
//                            new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
//                            MqttMessageIdVariableHeader.from(4),
//                            new MqttUnsubscribePayload(topicSubscriptions));
                    //mqtt订阅ack消息
                    MqttSubAckMessage mqttMessage = new MqttSubAckMessage(
                            new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(4),
                            new MqttSubAckPayload(1));

                    //发送消息
                    int i = 0;
                    for (; i < 1; i++) {
                        channel.writeAndFlush(mqttMessage);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.out.println("出错了:" + exc.getMessage());
            }
        });


    }

}
