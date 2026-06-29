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


import com.gettyio.core.handler.codec.DecoderResult;
import com.gettyio.core.util.ObjectUtil;

/**
 * MQTT 消息。
 * <p>所有 MQTT 消息类型统一使用此类表示，通过 {@link MqttFixedHeader#messageType()}
 * 区分不同报文。可变头部和负载以 {@code Object} 存储，使用时按消息类型转型。</p>
 *
 * @see MqttFixedHeader
 * @see MqttDecoder
 * @see MqttEncoder
 */
public class MqttMessage {

    private final MqttFixedHeader mqttFixedHeader;
    private final Object variableHeader;
    private final Object payload;
    private final DecoderResult decoderResult;

    public MqttMessage(MqttFixedHeader mqttFixedHeader) {
        this(mqttFixedHeader, null, null);
    }

    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader) {
        this(mqttFixedHeader, variableHeader, null);
    }

    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        this(mqttFixedHeader, variableHeader, payload, DecoderResult.SUCCESS);
    }

    public MqttMessage(
            MqttFixedHeader mqttFixedHeader,
            Object variableHeader,
            Object payload,
            DecoderResult decoderResult) {
        this.mqttFixedHeader = mqttFixedHeader;
        this.variableHeader = variableHeader;
        this.payload = payload;
        this.decoderResult = decoderResult;
    }

    /**
     * 根据消息类型创建对应的 MQTT 消息实例。
     * <p>替代原 MqttMessageFactory，按消息类型组装可变头部和负载。</p>
     *
     * @param mqttFixedHeader 固定头部
     * @param variableHeader  可变头部
     * @param payload         负载
     * @return 组装好的 MQTT 消息
     */
    public static MqttMessage newMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
            case CONNACK:
            case PUBLISH:
            case SUBSCRIBE:
            case SUBACK:
            case UNSUBSCRIBE:
            case UNSUBACK:
                return new MqttMessage(mqttFixedHeader, variableHeader, payload);

            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                return new MqttMessage(mqttFixedHeader, variableHeader);

            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                return new MqttMessage(mqttFixedHeader);

            default:
                throw new IllegalArgumentException("unknown message type: " + mqttFixedHeader.messageType());
        }
    }

    /**
     * 创建解码失败的消息
     */
    public static MqttMessage newInvalidMessage(Throwable cause) {
        return new MqttMessage(null, null, null, DecoderResult.failure(cause));
    }

    /**
     * 创建解码失败的消息（含部分已解析的头部信息）
     */
    public static MqttMessage newInvalidMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Throwable cause) {
        return new MqttMessage(mqttFixedHeader, variableHeader, null, DecoderResult.failure(cause));
    }

    /** 获取固定头部 */
    public MqttFixedHeader fixedHeader() {
        return mqttFixedHeader;
    }

    /** 获取可变头部（按消息类型转型） */
    public Object variableHeader() {
        return variableHeader;
    }

    /** 获取负载（按消息类型转型） */
    public Object payload() {
        return payload;
    }

    /** 获取解码结果 */
    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public String toString() {
        return new StringBuilder(ObjectUtil.simpleClassName(this))
            .append('[')
            .append("fixedHeader=").append(fixedHeader() != null ? fixedHeader().toString() : "")
            .append(", variableHeader=").append(variableHeader() != null ? variableHeader.toString() : "")
            .append(", payload=").append(payload() != null ? payload.toString() : "")
            .append(']')
            .toString();
    }
}
