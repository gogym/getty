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
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.handler.codec.DecoderException;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.isValidClientId;
import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.isValidMessageId;
import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.isValidPublishTopicName;
import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.resetUnusedFields;
import static com.gettyio.expansion.handler.codec.mqtt.MqttCodecUtil.validateFixedHeader;


/**
 * MQTT 消息解码器，基于 Netty MQTT Codec 改造。
 * <p>
 * 采用状态机模式依次解码固定头部、可变头部和负载：
 * <ol>
 *   <li>{@code READ_FIXED_HEADER} — 解码固定头部（1字节标志 + 可变长度剩余长度）</li>
 *   <li>{@code READ_VARIABLE_HEADER} — 解码可变头部（根据消息类型不同）</li>
 *   <li>{@code READ_PAYLOAD} — 解码负载数据</li>
 * </ol>
 * 支持 MQTT v3.1 和 v3.1.1 协议。
 * </p>
 *
 * @author gogym
 * @see MqttEncoder
 * @see MqttMessage
 */
public final class MqttDecoder extends ByteToMessageDecoder {

    /** 默认消息最大字节数：8192 (8KB) */
    private static final int DEFAULT_MAX_BYTES_IN_MESSAGE = 8 * 1024;

    /**
     * 解码器状态机。
     * 从 {@code READ_FIXED_HEADER} 开始，依次经过
     * {@code READ_VARIABLE_HEADER}、{@code READ_PAYLOAD}，
     * 解码异常时进入 {@code BAD_MESSAGE} 状态。
     */
    private enum DecoderState {
        /** 读取固定头部 */
        READ_FIXED_HEADER,
        /** 读取可变头部 */
        READ_VARIABLE_HEADER,
        /** 读取负载 */
        READ_PAYLOAD,
        /** 消息异常，等待断开连接 */
        BAD_MESSAGE
    }

    private MqttFixedHeader mqttFixedHeader;
    private Object variableHeader;
    private int bytesRemainingInVariablePart;
    private final int maxBytesInMessage;
    private DecoderState state;

    /**
     * 获取当前解码器状态
     *
     * @return 当前状态
     */
    private DecoderState state() {
        return state;
    }

    /**
     * 设置解码器状态
     *
     * @param newState 新状态
     * @return 旧状态
     */
    private DecoderState state(DecoderState newState) {
        DecoderState oldState = state;
        state = newState;
        return oldState;
    }

    /**
     * 更新解码器状态（检查点）
     *
     * @param state 新状态
     */
    private void checkpoint(DecoderState state) {
        state(state);
    }


    public MqttDecoder() {
        this(DEFAULT_MAX_BYTES_IN_MESSAGE);
    }

    /**
     * 创建指定最大消息字节数的解码器
     *
     * @param maxBytesInMessage 单条消息最大字节数，超过此值将拒绝消息
     */
    public MqttDecoder(int maxBytesInMessage) {
        state = DecoderState.READ_FIXED_HEADER;
        this.maxBytesInMessage = maxBytesInMessage;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        AutoByteBuffer buffer = AutoByteBuffer.newByteBuffer().writeBytes((byte[]) in);
        MqttMessage mqttMessage = null;
        switch (state()) {
            case READ_FIXED_HEADER:
                try {
                    mqttFixedHeader = decodeFixedHeader(buffer);
                    bytesRemainingInVariablePart = mqttFixedHeader.remainingLength();
                    checkpoint(DecoderState.READ_VARIABLE_HEADER);
                    // fall through
                } catch (Exception cause) {
                    mqttMessage = invalidMessage(cause);
                    break;
                }

            case READ_VARIABLE_HEADER:
                try {
                    final Result<?> decodedVariableHeader = decodeVariableHeader(buffer, mqttFixedHeader);
                    variableHeader = decodedVariableHeader.value;
                    if (bytesRemainingInVariablePart > maxBytesInMessage) {
                        throw new DecoderException("too large message: " + bytesRemainingInVariablePart + " bytes");
                    }
                    bytesRemainingInVariablePart -= decodedVariableHeader.numberOfBytesConsumed;
                    checkpoint(DecoderState.READ_PAYLOAD);
                    // fall through
                } catch (Exception cause) {
                    mqttMessage = invalidMessage(cause);
                    break;
                }

            case READ_PAYLOAD:
                try {
                    final Result<?> decodedPayload = decodePayload(buffer, mqttFixedHeader.messageType(), bytesRemainingInVariablePart, variableHeader);
                    bytesRemainingInVariablePart -= decodedPayload.numberOfBytesConsumed;
                    if (bytesRemainingInVariablePart != 0) {
                        throw new DecoderException("non-zero remaining payload bytes: " + bytesRemainingInVariablePart + " (" + mqttFixedHeader.messageType() + ')');
                    }
                    checkpoint(DecoderState.READ_FIXED_HEADER);
                    mqttMessage = MqttMessageFactory.newMessage(mqttFixedHeader, variableHeader, decodedPayload.value);
                    mqttFixedHeader = null;
                    variableHeader = null;
                    break;
                } catch (Exception cause) {
                    mqttMessage = invalidMessage(cause);
                    break;
                }

            case BAD_MESSAGE:
                // Keep discarding until disconnection.
                buffer.skipBytes(buffer.readableBytes());
                break;

            default:
                // Shouldn't reach here.
                throw new Error();
        }
        super.channelRead(ctx,mqttMessage);
    }

    /**
     * 创建无效消息并切换解码器到 {@code BAD_MESSAGE} 状态
     */
    private MqttMessage invalidMessage(Throwable cause) {
        checkpoint(DecoderState.BAD_MESSAGE);
        return MqttMessageFactory.newInvalidMessage(mqttFixedHeader, variableHeader, cause);
    }

    /**
     * 解码固定头部：1字节标志位 + 可变长度编码的剩余长度。
     *
     * @param buffer 输入缓冲
     * @return 解码后的固定头部
     * @throws DecoderException 剩余长度超过4字节或头部校验失败
     */
    private static MqttFixedHeader decodeFixedHeader(AutoByteBuffer buffer) throws Exception {
        short b1 = buffer.readUnsignedByte();

        MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
        boolean dupFlag = (b1 & 0x08) == 0x08;
        int qosLevel = (b1 & 0x06) >> 1;
        boolean retain = (b1 & 0x01) != 0;

        int remainingLength = 0;
        int multiplier = 1;
        short digit;
        int loops = 0;
        do {
            digit = buffer.readUnsignedByte();
            remainingLength += (digit & 127) * multiplier;
            multiplier *= 128;
            loops++;
        } while ((digit & 128) != 0 && loops < 4);

        // MQTT protocol limits Remaining Length to 4 bytes
        if (loops == 4 && (digit & 128) != 0) {
            throw new DecoderException("remaining length exceeds 4 digits (" + messageType + ')');
        }
        MqttFixedHeader decodedFixedHeader = new MqttFixedHeader(messageType, dupFlag, MqttQoS.valueOf(qosLevel), retain, remainingLength);
        return validateFixedHeader(resetUnusedFields(decodedFixedHeader));
    }

    /**
     * 根据消息类型解码可变头部
     *
     * @param buffer          输入缓冲
     * @param mqttFixedHeader 对应的固定头部
     * @return 解码结果，包含可变头部和消耗字节数
     */
    private static Result<?> decodeVariableHeader(AutoByteBuffer buffer, MqttFixedHeader mqttFixedHeader) throws Exception {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
                return decodeConnectionVariableHeader(buffer);

            case CONNACK:
                return decodeConnAckVariableHeader(buffer);

            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case SUBACK:
            case UNSUBACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case PUBREL:
                return decodeMessageIdVariableHeader(buffer);

            case PUBLISH:
                return decodePublishVariableHeader(buffer, mqttFixedHeader);

            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                // Empty variable header
                return new Result<Object>(null, 0);
        }
        return new Result<Object>(null, 0); //should never reach here
    }

    private static Result<MqttConnectVariableHeader> decodeConnectionVariableHeader(AutoByteBuffer buffer) throws Exception {
        final Result<String> protoString = decodeString(buffer);
        int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

        final byte protocolLevel = buffer.readByte();
        numberOfBytesConsumed += 1;

        final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(protoString.value, protocolLevel);

        final int b1 = buffer.readUnsignedByte();
        numberOfBytesConsumed += 1;

        final Result<Integer> keepAlive = decodeMsbLsb(buffer);
        numberOfBytesConsumed += keepAlive.numberOfBytesConsumed;

        final boolean hasUserName = (b1 & 0x80) == 0x80;
        final boolean hasPassword = (b1 & 0x40) == 0x40;
        final boolean willRetain = (b1 & 0x20) == 0x20;
        final int willQos = (b1 & 0x18) >> 3;
        final boolean willFlag = (b1 & 0x04) == 0x04;
        final boolean cleanSession = (b1 & 0x02) == 0x02;
        if (mqttVersion == MqttVersion.MQTT_3_1_1) {
            final boolean zeroReservedFlag = (b1 & 0x01) == 0x0;
            if (!zeroReservedFlag) {
                // MQTT v3.1.1: The Server MUST validate that the reserved flag in the CONNECT Control Packet is
                // set to zero and disconnect the Client if it is not zero.
                // See http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349230
                throw new DecoderException("non-zero reserved flag");
            }
        }

        final MqttConnectVariableHeader mqttConnectVariableHeader = new MqttConnectVariableHeader(
                mqttVersion.protocolName(),
                mqttVersion.protocolLevel(),
                hasUserName,
                hasPassword,
                willRetain,
                willQos,
                willFlag,
                cleanSession,
                keepAlive.value);
        return new Result<MqttConnectVariableHeader>(mqttConnectVariableHeader, numberOfBytesConsumed);
    }

    private static Result<MqttConnAckVariableHeader> decodeConnAckVariableHeader(AutoByteBuffer buffer) throws Exception {
        final boolean sessionPresent = (buffer.readUnsignedByte() & 0x01) == 0x01;
        byte returnCode = buffer.readByte();
        final int numberOfBytesConsumed = 2;
        final MqttConnAckVariableHeader mqttConnAckVariableHeader = new MqttConnAckVariableHeader(MqttConnectReturnCode.valueOf(returnCode), sessionPresent);
        return new Result<MqttConnAckVariableHeader>(mqttConnAckVariableHeader, numberOfBytesConsumed);
    }

    private static Result<MqttMessageIdVariableHeader> decodeMessageIdVariableHeader(AutoByteBuffer buffer) throws Exception {
        final Result<Integer> messageId = decodeMessageId(buffer);
        return new Result<MqttMessageIdVariableHeader>(MqttMessageIdVariableHeader.from(messageId.value), messageId.numberOfBytesConsumed);
    }

    private static Result<MqttPublishVariableHeader> decodePublishVariableHeader(AutoByteBuffer buffer, MqttFixedHeader mqttFixedHeader) throws Exception {
        final Result<String> decodedTopic = decodeString(buffer);
        if (!isValidPublishTopicName(decodedTopic.value)) {
            throw new DecoderException("invalid publish topic name: " + decodedTopic.value + " (contains wildcards)");
        }
        int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

        int messageId = -1;
        if (mqttFixedHeader.qosLevel().value() > 0) {
            final Result<Integer> decodedMessageId = decodeMessageId(buffer);
            messageId = decodedMessageId.value;
            numberOfBytesConsumed += decodedMessageId.numberOfBytesConsumed;
        }
        final MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader(decodedTopic.value, messageId);
        return new Result<MqttPublishVariableHeader>(mqttPublishVariableHeader, numberOfBytesConsumed);
    }

    private static Result<Integer> decodeMessageId(AutoByteBuffer buffer) throws Exception {
        final Result<Integer> messageId = decodeMsbLsb(buffer);
        if (!isValidMessageId(messageId.value)) {
            throw new DecoderException("invalid messageId: " + messageId.value);
        }
        return messageId;
    }

    /**
     * 解码负载数据
     *
     * @param buffer                       输入缓冲
     * @param messageType                  消息类型
     * @param bytesRemainingInVariablePart 剩余字节数
     * @param variableHeader               可变头部
     * @return 解码结果，包含负载和消耗字节数
     */
    private static Result<?> decodePayload(AutoByteBuffer buffer, MqttMessageType messageType, int bytesRemainingInVariablePart, Object variableHeader) throws Exception {
        switch (messageType) {
            case CONNECT:
                return decodeConnectionPayload(buffer, (MqttConnectVariableHeader) variableHeader);

            case SUBSCRIBE:
                return decodeSubscribePayload(buffer, bytesRemainingInVariablePart);

            case SUBACK:
                return decodeSubackPayload(buffer, bytesRemainingInVariablePart);

            case UNSUBSCRIBE:
                return decodeUnsubscribePayload(buffer, bytesRemainingInVariablePart);

            case PUBLISH:
                return decodePublishPayload(buffer, bytesRemainingInVariablePart);

            default:
                // unknown payload , no byte consumed
                return new Result<Object>(null, 0);
        }
    }

    private static Result<MqttConnectPayload> decodeConnectionPayload(AutoByteBuffer buffer, MqttConnectVariableHeader mqttConnectVariableHeader) throws Exception {
        final Result<String> decodedClientId = decodeString(buffer);
        final String decodedClientIdValue = decodedClientId.value;
        final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(mqttConnectVariableHeader.name(), (byte) mqttConnectVariableHeader.version());
        if (!isValidClientId(mqttVersion, decodedClientIdValue)) {
            throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + decodedClientIdValue);
        }
        int numberOfBytesConsumed = decodedClientId.numberOfBytesConsumed;

        Result<String> decodedWillTopic = null;
        Result<byte[]> decodedWillMessage = null;
        if (mqttConnectVariableHeader.isWillFlag()) {
            decodedWillTopic = decodeString(buffer, 0, 32767);
            numberOfBytesConsumed += decodedWillTopic.numberOfBytesConsumed;
            decodedWillMessage = decodeByteArray(buffer);
            numberOfBytesConsumed += decodedWillMessage.numberOfBytesConsumed;
        }
        Result<String> decodedUserName = null;
        Result<byte[]> decodedPassword = null;
        if (mqttConnectVariableHeader.hasUserName()) {
            decodedUserName = decodeString(buffer);
            numberOfBytesConsumed += decodedUserName.numberOfBytesConsumed;
        }
        if (mqttConnectVariableHeader.hasPassword()) {
            decodedPassword = decodeByteArray(buffer);
            numberOfBytesConsumed += decodedPassword.numberOfBytesConsumed;
        }

        final MqttConnectPayload mqttConnectPayload = new MqttConnectPayload(
                decodedClientId.value,
                decodedWillTopic != null ? decodedWillTopic.value : null,
                decodedWillMessage != null ? decodedWillMessage.value : null,
                decodedUserName != null ? decodedUserName.value : null,
                decodedPassword != null ? decodedPassword.value : null);
        return new Result<MqttConnectPayload>(mqttConnectPayload, numberOfBytesConsumed);
    }

    private static Result<MqttSubscribePayload> decodeSubscribePayload(AutoByteBuffer buffer, int bytesRemainingInVariablePart) throws Exception {
        final List<MqttTopicSubscription> subscribeTopics = new ArrayList<MqttTopicSubscription>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final Result<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            int qos = buffer.readUnsignedByte() & 0x03;
            numberOfBytesConsumed++;
            subscribeTopics.add(new MqttTopicSubscription(decodedTopicName.value, MqttQoS.valueOf(qos)));
        }
        return new Result<MqttSubscribePayload>(new MqttSubscribePayload(subscribeTopics), numberOfBytesConsumed);
    }

    private static Result<MqttSubAckPayload> decodeSubackPayload(AutoByteBuffer buffer, int bytesRemainingInVariablePart) throws Exception {
        final List<Integer> grantedQos = new ArrayList<Integer>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            int qos = buffer.readUnsignedByte();
            if (qos != MqttQoS.FAILURE.value()) {
                qos &= 0x03;
            }
            numberOfBytesConsumed++;
            grantedQos.add(qos);
        }
        return new Result<MqttSubAckPayload>(new MqttSubAckPayload(grantedQos), numberOfBytesConsumed);
    }

    private static Result<MqttUnsubscribePayload> decodeUnsubscribePayload(AutoByteBuffer buffer, int bytesRemainingInVariablePart) throws Exception {
        final List<String> unsubscribeTopics = new ArrayList<String>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final Result<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            unsubscribeTopics.add(decodedTopicName.value);
        }
        return new Result<MqttUnsubscribePayload>(new MqttUnsubscribePayload(unsubscribeTopics), numberOfBytesConsumed);
    }

    private static Result<AutoByteBuffer> decodePublishPayload(AutoByteBuffer buffer, int bytesRemainingInVariablePart) throws Exception {
        AutoByteBuffer b = buffer.readRetainedSlice(bytesRemainingInVariablePart);
        return new Result<AutoByteBuffer>(b, bytesRemainingInVariablePart);
    }

    private static Result<String> decodeString(AutoByteBuffer buffer) throws Exception {
        return decodeString(buffer, 0, Integer.MAX_VALUE);
    }

    private static Result<String> decodeString(AutoByteBuffer buffer, int minBytes, int maxBytes) throws Exception {
        final Result<Integer> decodedSize = decodeMsbLsb(buffer);
        int size = decodedSize.value;
        int numberOfBytesConsumed = decodedSize.numberOfBytesConsumed;
        if (size < minBytes || size > maxBytes) {
            buffer.skipBytes(size);
            numberOfBytesConsumed += size;
            return new Result<String>(null, numberOfBytesConsumed);
        }
        String s = buffer.decodeString(buffer.readerIndex(), size, CharsetUtil.UTF_8);
        buffer.skipBytes(size);
        numberOfBytesConsumed += size;
        return new Result<String>(s, numberOfBytesConsumed);
    }

    private static Result<byte[]> decodeByteArray(AutoByteBuffer buffer) throws Exception {
        final Result<Integer> decodedSize = decodeMsbLsb(buffer);
        int size = decodedSize.value;
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        return new Result<byte[]>(bytes, decodedSize.numberOfBytesConsumed + size);
    }

    private static Result<Integer> decodeMsbLsb(AutoByteBuffer buffer) throws Exception {
        return decodeMsbLsb(buffer, 0, 65535);
    }

    private static Result<Integer> decodeMsbLsb(AutoByteBuffer buffer, int min, int max) throws Exception {
        short msbSize = buffer.readUnsignedByte();
        short lsbSize = buffer.readUnsignedByte();
        final int numberOfBytesConsumed = 2;
        int result = msbSize << 8 | lsbSize;
        if (result < min || result > max) {
            result = -1;
        }
        return new Result<Integer>(result, numberOfBytesConsumed);
    }

    /**
     * 解码结果封装，包含解码值和消耗的字节数
     */
    private static final class Result<T> {

        private final T value;
        private final int numberOfBytesConsumed;

        Result(T value, int numberOfBytesConsumed) {
            this.value = value;
            this.numberOfBytesConsumed = numberOfBytesConsumed;
        }
    }
}
