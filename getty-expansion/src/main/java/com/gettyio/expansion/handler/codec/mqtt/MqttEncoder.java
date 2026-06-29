/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.expansion.handler.codec.mqtt;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.DecoderException;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;

import java.util.List;

import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.isValidClientId;


/**
 * MQTT 消息编码器，基于 Netty MQTT Codec 改造。
 * <p>
 * 将 {@link MqttMessage} 编码为字节数组，支持 MQTT v3.1 和 v3.1.1 协议定义的全部消息类型：
 * CONNECT、CONNACK、PUBLISH、SUBSCRIBE、UNSUBSCRIBE、SUBACK、UNSUBACK、
 * PUBACK、PUBREC、PUBREL、PUBCOMP、PINGREQ、PINGRESP、DISCONNECT。
 * </p>
 * <p>本类为单例模式，通过 {@link #INSTANCE} 获取唯一实例。</p>
 *
 * @author gogym
 * @see MqttMessage
 * @see MqttDecoder
 */
public final class MqttEncoder extends MessageToByteEncoder {

    /** 空字节数组常量，用于替代 null 值 */
    private static final byte[] EMPTY_BYTES = {};

    /** 单例实例 */
    public static final MqttEncoder INSTANCE = new MqttEncoder();

    private MqttEncoder() {
    }

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        AutoByteBuffer autoByteBuffer = doEncode((MqttMessage) obj);
        byte[] bytes = autoByteBuffer.readableBytesArray();
        PooledByteBuffer buf = ctx.channel().getByteBufferPool().acquire(bytes.length);
        buf.writeBytes(bytes);
        super.channelWrite(ctx, buf);
    }

    /**
     * 编码 MQTT 消息为字节缓冲。
     *
     * @param message 待编码的 MQTT 消息
     * @return 编码后的字节缓冲
     * @throws IllegalArgumentException 未知的消息类型
     */
    static AutoByteBuffer doEncode(MqttMessage message) {

        switch (message.fixedHeader().messageType()) {
            case CONNECT:
                return encodeConnectMessage(message);

            case CONNACK:
                return encodeConnAckMessage(message);

            case PUBLISH:
                return encodePublishMessage(message);

            case SUBSCRIBE:
                return encodeSubscribeMessage(message);

            case UNSUBSCRIBE:
                return encodeUnsubscribeMessage(message);

            case SUBACK:
                return encodeSubAckMessage(message);

            case UNSUBACK:
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(message);

            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                return encodeMessageWithOnlySingleByteFixedHeader(message);

            default:
                throw new IllegalArgumentException("Unknown message type: " + message.fixedHeader().messageType().value());
        }
    }

    private static AutoByteBuffer encodeConnectMessage(MqttMessage message) {
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttConnectVariableHeader variableHeader = (MqttConnectVariableHeader) message.variableHeader();
        MqttConnectPayload payload = (MqttConnectPayload) message.payload();
        MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(variableHeader.name(), (byte) variableHeader.version());

        // as MQTT 3.1 & 3.1.1 spec, If the User Name Flag is set to 0, the Password Flag MUST be set to 0
        if (!variableHeader.hasUserName() && variableHeader.hasPassword()) {
            throw new DecoderException("Without a username, the password MUST be not set");
        }

        // Client id
        String clientIdentifier = payload.clientIdentifier();
        if (!isValidClientId(mqttVersion, clientIdentifier)) {
            throw new IllegalArgumentException("invalid clientIdentifier: " + clientIdentifier);
        }
        byte[] clientIdentifierBytes = encodeStringUtf8(clientIdentifier);
        payloadBufferSize += 2 + clientIdentifierBytes.length;

        // Will topic and message
        String willTopic = payload.willTopic();
        byte[] willTopicBytes = willTopic != null ? encodeStringUtf8(willTopic) : EMPTY_BYTES;
        byte[] willMessage = payload.willMessageInBytes();
        byte[] willMessageBytes = willMessage != null ? willMessage : EMPTY_BYTES;
        if (variableHeader.isWillFlag()) {
            payloadBufferSize += 2 + willTopicBytes.length;
            payloadBufferSize += 2 + willMessageBytes.length;
        }

        String userName = payload.userName();
        byte[] userNameBytes = userName != null ? encodeStringUtf8(userName) : EMPTY_BYTES;
        if (variableHeader.hasUserName()) {
            payloadBufferSize += 2 + userNameBytes.length;
        }

        byte[] password = payload.passwordInBytes();
        byte[] passwordBytes = password != null ? password : EMPTY_BYTES;
        if (variableHeader.hasPassword()) {
            payloadBufferSize += 2 + passwordBytes.length;
        }

        // Fixed header
        byte[] protocolNameBytes = mqttVersion.protocolNameBytes();
        int variableHeaderBufferSize = 2 + protocolNameBytes.length + 4;
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));

        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(protocolNameBytes.length);
        buf.writeBytes(protocolNameBytes);

        buf.write(variableHeader.version());
        buf.write(getConnVariableHeaderFlag(variableHeader));
        buf.writeShort(variableHeader.keepAliveTimeSeconds());

        // Payload
        buf.writeShort(clientIdentifierBytes.length);
        buf.writeBytes(clientIdentifierBytes, 0, clientIdentifierBytes.length);
        if (variableHeader.isWillFlag()) {
            buf.writeShort(willTopicBytes.length);
            buf.writeBytes(willTopicBytes, 0, willTopicBytes.length);
            buf.writeShort(willMessageBytes.length);
            buf.writeBytes(willMessageBytes, 0, willMessageBytes.length);
        }
        if (variableHeader.hasUserName()) {
            buf.writeShort(userNameBytes.length);
            buf.writeBytes(userNameBytes, 0, userNameBytes.length);
        }
        if (variableHeader.hasPassword()) {
            buf.writeShort(passwordBytes.length);
            buf.writeBytes(passwordBytes, 0, passwordBytes.length);
        }
        return buf;
    }

    private static int getConnVariableHeaderFlag(MqttConnectVariableHeader variableHeader) {
        int flagByte = 0;
        if (variableHeader.hasUserName()) {
            flagByte |= 0x80;
        }
        if (variableHeader.hasPassword()) {
            flagByte |= 0x40;
        }
        if (variableHeader.isWillRetain()) {
            flagByte |= 0x20;
        }
        flagByte |= (variableHeader.willQos() & 0x03) << 3;
        if (variableHeader.isWillFlag()) {
            flagByte |= 0x04;
        }
        if (variableHeader.isCleanSession()) {
            flagByte |= 0x02;
        }
        return flagByte;
    }

    private static AutoByteBuffer encodeConnAckMessage(MqttMessage message) {
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(4);
        buf.write(getFixedHeaderByte1(message.fixedHeader()));
        buf.write(2);
        MqttConnAckVariableHeader variableHeader = (MqttConnAckVariableHeader) message.variableHeader();
        buf.write(variableHeader.isSessionPresent() ? 0x01 : 0x00);
        buf.write(variableHeader.connectReturnCode().byteValue());

        return buf;
    }

    private static AutoByteBuffer encodeSubscribeMessage(MqttMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttSubscribePayload payload = (MqttSubscribePayload) message.payload();

        // 预计算负载大小并缓存 UTF-8 字节，避免重复遍历
        List<MqttTopicSubscription> topics = payload.topicSubscriptions();
        String[] topicNames = new String[topics.size()];
        byte[][] topicNameBytesArr = new byte[topics.size()][];
        for (int i = 0; i < topics.size(); i++) {
            topicNames[i] = topics.get(i).topicName();
            topicNameBytesArr[i] = encodeStringUtf8(topicNames[i]);
            payloadBufferSize += 2 + topicNameBytesArr[i].length + 1;
        }

        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));
        writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        buf.writeShort(variableHeader.messageId());

        // Payload（使用缓存的数据，无需重复遍历）
        for (int i = 0; i < topics.size(); i++) {
            buf.writeShort(topicNameBytesArr[i].length);
            buf.writeBytes(topicNameBytesArr[i], 0, topicNameBytesArr[i].length);
            buf.write(topics.get(i).qualityOfService().value());
        }

        return buf;
    }

    private static AutoByteBuffer encodeUnsubscribeMessage(MqttMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttUnsubscribePayload payload = (MqttUnsubscribePayload) message.payload();

        for (String topicName : payload.topics()) {
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            payloadBufferSize += 2 + topicNameBytes.length;
        }

        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));
        writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        int messageId = variableHeader.messageId();
        buf.writeShort(messageId);

        // Payload
        for (String topicName : payload.topics()) {
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            buf.writeShort(topicNameBytes.length);
            buf.writeBytes(topicNameBytes, 0, topicNameBytes.length);
        }

        return buf;
    }

    private static AutoByteBuffer encodeSubAckMessage(MqttMessage message) {
        int variableHeaderBufferSize = 2;
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttSubAckPayload payload = (MqttSubAckPayload) message.payload();
        int payloadBufferSize = payload.grantedQoSLevels().size();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(message.fixedHeader()));
        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(variableHeader.messageId());
        for (int qos : payload.grantedQoSLevels()) {
            buf.write(qos);
        }

        return buf;
    }

    private static AutoByteBuffer encodePublishMessage(MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPublishVariableHeader variableHeader = (MqttPublishVariableHeader) message.variableHeader();
        //复制一份数据
        AutoByteBuffer payload = ((AutoByteBuffer) message.payload()).duplicate();

        String topicName = variableHeader.topicName();
        byte[] topicNameBytes = encodeStringUtf8(topicName);

        int variableHeaderBufferSize = 2 + topicNameBytes.length + (mqttFixedHeader.qosLevel().value() > 0 ? 2 : 0);
        int payloadBufferSize = payload.readableBytes();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));
        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(topicNameBytes.length);
        buf.writeBytes(topicNameBytes);
        if (mqttFixedHeader.qosLevel().value() > 0) {
            buf.writeShort(variableHeader.packetId());
        }
        buf.writeBytes(payload);

        return buf;
    }

    private static AutoByteBuffer encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        int msgId = variableHeader.messageId();

        int variableHeaderBufferSize = 2; // variable part only has a message id
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variableHeaderBufferSize);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));
        writeVariableLengthInt(buf, variableHeaderBufferSize);
        buf.writeShort(msgId);

        return buf;
    }

    private static AutoByteBuffer encodeMessageWithOnlySingleByteFixedHeader(MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(2);
        buf.write(getFixedHeaderByte1(mqttFixedHeader));
        buf.write(0);
        return buf;
    }

    /**
     * 组装固定头部第一个字节：消息类型(4位) + DUP(1位) + QoS(2位) + Retain(1位)
     */
    private static int getFixedHeaderByte1(MqttFixedHeader header) {
        int ret = header.messageType().value() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.qosLevel().value() << 1;
        if (header.isRetain()) {
            ret |= 0x01;
        }
        return ret;
    }

    /**
     * 将整数编码为 MQTT 可变长度格式（1~4 字节）写入缓冲。
     * <p>每个字节的低 7 位表示数据，最高位为延续标志（1=后续还有字节）。</p>
     */
    private static void writeVariableLengthInt(AutoByteBuffer buf, int num) {
        do {
            int digit = num % 128;
            num /= 128;
            if (num > 0) {
                digit |= 0x80;
            }
            buf.write(digit);
        } while (num > 0);
    }

    /**
     * 计算整数以 MQTT 可变长度格式编码所需的字节数（1~4）
     */
    private static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

    /**
     * 将字符串编码为 UTF-8 字节数组
     */
    private static byte[] encodeStringUtf8(String s) {
        return s.getBytes(CharsetUtil.UTF_8);
    }
}
