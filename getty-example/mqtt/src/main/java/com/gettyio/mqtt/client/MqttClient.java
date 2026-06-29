package com.gettyio.mqtt.client;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.mqtt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT 客户端测试示例。
 * <p>连接成功后依次发送所有类型的 MQTT 消息，服务端回显回来，验证编解码正确性。</p>
 */
public class MqttClient {

    public static void main(String[] args) throws Exception {
        test(9999);
    }

    private static void test(int port) {

        AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                ChannelPipeline defaultChannelPipeline = channel.getChannelPipeline();
                // 添加 mqtt 编解码器
                defaultChannelPipeline.addLast(MqttEncoder.INSTANCE);
                defaultChannelPipeline.addLast(new MqttDecoder());
                // 定义消息处理器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(final AbstractSocketChannel channel) {
                try {
                    // 稍等一下让连接稳定
                    Thread.sleep(200);

                    System.out.println("\n========== 开始发送各种类型的 MQTT 消息 ==========\n");

                    // 1. CONNECT
                    sendAndPrint("CONNECT", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnectVariableHeader("MQTT", 4, true, true, false, 0, true, true, 60),
                            new MqttConnectPayload("client-001", null, null, "admin", "123456".getBytes())));
                    Thread.sleep(100);

                    // 2. CONNACK
                    sendAndPrint("CONNACK", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, true),
                            null));
                    Thread.sleep(100);

                    // 3. PUBLISH (QoS 0)
                    sendAndPrint("PUBLISH", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttPublishVariableHeader("test/topic", 0),
                            AutoByteBuffer.newByteBuffer().writeBytes("Hello MQTT!".getBytes())));
                    Thread.sleep(100);

                    // 4. PUBLISH (QoS 1)
                    sendAndPrint("PUBLISH-QoS1", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                            new MqttPublishVariableHeader("test/topic/qos1", 100),
                            AutoByteBuffer.newByteBuffer().writeBytes("QoS1 消息".getBytes())));
                    Thread.sleep(100);

                    // 5. PUBACK
                    sendAndPrint("PUBACK", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(100),
                            null));
                    Thread.sleep(100);

                    // 6. PUBREC
                    sendAndPrint("PUBREC", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(101),
                            null));
                    Thread.sleep(100);

                    // 7. PUBREL
                    sendAndPrint("PUBREL", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(101),
                            null));
                    Thread.sleep(100);

                    // 8. PUBCOMP
                    sendAndPrint("PUBCOMP", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(101),
                            null));
                    Thread.sleep(100);

                    // 9. SUBSCRIBE
                    List<MqttTopicSubscription> subs = new ArrayList<>();
                    subs.add(new MqttTopicSubscription("sensor/temperature", MqttQoS.AT_LEAST_ONCE));
                    subs.add(new MqttTopicSubscription("sensor/humidity", MqttQoS.AT_MOST_ONCE));
                    sendAndPrint("SUBSCRIBE", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(200),
                            new MqttSubscribePayload(subs)));
                    Thread.sleep(100);

                    // 10. SUBACK
                    List<Integer> qosList = new ArrayList<>();
                    qosList.add(1);
                    qosList.add(0);
                    sendAndPrint("SUBACK", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(200),
                            new MqttSubAckPayload(qosList)));
                    Thread.sleep(100);

                    // 11. UNSUBSCRIBE
                    List<String> topics = new ArrayList<>();
                    topics.add("sensor/temperature");
                    topics.add("sensor/humidity");
                    sendAndPrint("UNSUBSCRIBE", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(300),
                            new MqttUnsubscribePayload(topics)));
                    Thread.sleep(100);

                    // 12. UNSUBACK
                    sendAndPrint("UNSUBACK", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(300),
                            null));
                    Thread.sleep(100);

                    // 13. PINGREQ
                    sendAndPrint("PINGREQ", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            null, null));
                    Thread.sleep(100);

                    // 14. PINGRESP
                    sendAndPrint("PINGRESP", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            null, null));
                    Thread.sleep(100);

                    // 15. DISCONNECT
                    sendAndPrint("DISCONNECT", channel, MqttMessage.newMessage(
                            new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            null, null));

                    System.out.println("\n========== 全部消息发送完毕 ==========");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.out.println("连接失败: " + exc.getMessage());
            }
        });
    }

    /**
     * 发送消息并打印摘要
     */
    private static void sendAndPrint(String label, AbstractSocketChannel channel, MqttMessage message) {
        System.out.println("[发送] " + label);
        channel.writeAndFlush(message);
    }
}
