package com.gettyio.mqtt.client;


import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.mqtt.*;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * MQTT 客户端处理器 —— 打印服务端回显的消息详情。
 */
public class SimpleHandler extends SimpleChannelInboundHandler<MqttMessage> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[客户端] 连接服务器成功");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[客户端] 连接关闭了");
    }

    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, MqttMessage mqttMessage) {
        MqttMessageType type = mqttMessage.fixedHeader().messageType();
        System.out.println("[客户端] 收到回显: " + type);

        switch (type) {
            case CONNECT: {
                MqttConnectVariableHeader vh = (MqttConnectVariableHeader) mqttMessage.variableHeader();
                MqttConnectPayload p = (MqttConnectPayload) mqttMessage.payload();
                System.out.println("  -> 协议: " + vh.name() + " v" + vh.version()
                        + ", clientId=" + p.clientIdentifier());
                break;
            }
            case CONNACK: {
                MqttConnAckVariableHeader vh = (MqttConnAckVariableHeader) mqttMessage.variableHeader();
                System.out.println("  -> 返回码: " + vh.connectReturnCode()
                        + ", sessionPresent=" + vh.isSessionPresent());
                break;
            }
            case PUBLISH: {
                MqttPublishVariableHeader vh = (MqttPublishVariableHeader) mqttMessage.variableHeader();
                AutoByteBuffer payload = (AutoByteBuffer) mqttMessage.payload();
                byte[] bytes = new byte[payload.readableBytes()];
                try {
                    payload.readBytes(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("  -> topic=" + vh.topicName()
                        + ", payload=" + new String(bytes));
                break;
            }
            case SUBSCRIBE: {
                MqttSubscribePayload p = (MqttSubscribePayload) mqttMessage.payload();
                System.out.println("  -> 订阅主题:");
                for (MqttTopicSubscription ts : p.topicSubscriptions()) {
                    System.out.println("     " + ts.topicName() + " QoS=" + ts.qualityOfService());
                }
                break;
            }
            case SUBACK: {
                MqttSubAckPayload p = (MqttSubAckPayload) mqttMessage.payload();
                System.out.println("  -> grantedQoS=" + p.grantedQoSLevels());
                break;
            }
            case UNSUBSCRIBE: {
                MqttUnsubscribePayload p = (MqttUnsubscribePayload) mqttMessage.payload();
                System.out.println("  -> 取消订阅: " + p.topics());
                break;
            }
            case UNSUBACK:
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP: {
                MqttMessageIdVariableHeader vh = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
                System.out.println("  -> messageId=" + vh.messageId());
                break;
            }
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                System.out.println("  -> (无附加数据)");
                break;
            default:
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[客户端] 出错了");
        cause.printStackTrace();
    }
}
