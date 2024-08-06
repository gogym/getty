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
import com.gettyio.core.handler.codec.DecoderException;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;

import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.isValidClientId;


/**
 * MqttEncoder.java
 * @description:MQTT编码器，基于netty改造
 * @author:gogym
 * @date:2020/6/9
 * @copyright: Copyright by gettyio.com
 */
public final class MqttEncoder extends MessageToByteEncoder {

    public static final byte[] EMPTY_BYTES = {};
    public static final MqttEncoder INSTANCE = new MqttEncoder();

    private MqttEncoder() {
    }

    @Override
    public void channelWrite(ChannelHandlerContext ctx,Object obj) throws Exception {
        AutoByteBuffer autoByteBuffer = doEncode((MqttMessage) obj);
        super.channelWrite(ctx,autoByteBuffer.array());
    }

    /**
     * This is the main encoding method.
     * It's only visible for testing.
     *
     * @param message MQTT message to encode
     * @return ByteBuf with encoded bytes
     */
    static AutoByteBuffer doEncode(MqttMessage message) {

        switch (message.fixedHeader().messageType()) {
            case CONNECT:
                return encodeConnectMessage((MqttConnectMessage) message);

            case CONNACK:
                return encodeConnAckMessage((MqttConnAckMessage) message);

            case PUBLISH:
                return encodePublishMessage((MqttPublishMessage) message);

            case SUBSCRIBE:
                return encodeSubscribeMessage((MqttSubscribeMessage) message);

            case UNSUBSCRIBE:
                return encodeUnsubscribeMessage((MqttUnsubscribeMessage) message);

            case SUBACK:
                return encodeSubAckMessage((MqttSubAckMessage) message);

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

    private static AutoByteBuffer encodeConnectMessage(MqttConnectMessage message) {
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttConnectVariableHeader variableHeader = message.variableHeader();
        MqttConnectPayload payload = message.payload();
        MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(variableHeader.name(), (byte) variableHeader.version());

        // as MQTT 3.1 & 3.1.1 spec, If the User Name Flag is set to 0, the Password Flag MUST be set to 0
        if (!variableHeader.hasUserName() && variableHeader.hasPassword()) {
            throw new DecoderException("Without a username, the password MUST be not set");
        }

        // Client id
        String clientIdentifier = payload.clientIdentifier();
        if (!isValidClientId(mqttVersion, clientIdentifier)) {
            throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + clientIdentifier);
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

    private static AutoByteBuffer encodeConnAckMessage(MqttConnAckMessage message) {
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(4);
        buf.write(getFixedHeaderByte1(message.fixedHeader()));
        buf.write(2);
        buf.write(message.variableHeader().isSessionPresent() ? 0x01 : 0x00);
        buf.write(message.variableHeader().connectReturnCode().byteValue());

        return buf;
    }

    private static AutoByteBuffer encodeSubscribeMessage(MqttSubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = message.variableHeader();
        MqttSubscribePayload payload = message.payload();

        for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
            String topicName = topic.topicName();
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            payloadBufferSize += 2 + topicNameBytes.length;
            payloadBufferSize += 1;
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
        for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
            String topicName = topic.topicName();
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            buf.writeShort(topicNameBytes.length);
            buf.writeBytes(topicNameBytes, 0, topicNameBytes.length);
            buf.write(topic.qualityOfService().value());
        }

        return buf;
    }

    private static AutoByteBuffer encodeUnsubscribeMessage(MqttUnsubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = message.variableHeader();
        MqttUnsubscribePayload payload = message.payload();

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

    private static AutoByteBuffer encodeSubAckMessage(MqttSubAckMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = message.payload().grantedQoSLevels().size();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
        AutoByteBuffer buf = AutoByteBuffer.newByteBuffer(fixedHeaderBufferSize + variablePartSize);
        buf.write(getFixedHeaderByte1(message.fixedHeader()));
        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(message.variableHeader().messageId());
        for (int qos : message.payload().grantedQoSLevels()) {
            buf.write(qos);
        }

        return buf;
    }

    private static AutoByteBuffer encodePublishMessage(MqttPublishMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPublishVariableHeader variableHeader = message.variableHeader();
        //复制一份数据
        AutoByteBuffer payload = message.payload().duplicate();

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
            buf.write(variableHeader.messageId());
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

    private static int getFixedHeaderByte1(MqttFixedHeader header) {
        int ret = 0;
        ret |= header.messageType().value() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.qosLevel().value() << 1;
        if (header.isRetain()) {
            ret |= 0x01;
        }
        return ret;
    }

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

    private static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

    private static byte[] encodeStringUtf8(String s) {
        return s.getBytes(CharsetUtil.UTF_8);
    }
}
