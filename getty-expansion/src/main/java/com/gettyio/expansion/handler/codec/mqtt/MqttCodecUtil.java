/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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


import com.gettyio.core.handler.codec.DecoderException;

/**
 * MQTT 编解码工具类，提供协议级别的校验方法。
 * <p>包括主题名校验、消息ID校验、客户端ID校验、固定头部校验等。</p>
 */
final class MqttCodecUtil {

    private static final int MIN_CLIENT_ID_LENGTH = 1;
    private static final int MAX_CLIENT_ID_LENGTH = 23;

    /**
     * 校验发布主题名是否合法（不能包含通配符 '#' 和 '+'）
     *
     * @param topicName 主题名
     * @return 合法返回 true
     */
    static boolean isValidPublishTopicName(String topicName) {
        for (int i = 0; i < topicName.length(); i++) {
            char c = topicName.charAt(i);
            if (c == '#' || c == '+') {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验消息ID是否合法（MQTT 协议规定消息ID不能为 0）
     *
     * @param messageId 消息ID
     * @return 合法返回 true
     */
    static boolean isValidMessageId(int messageId) {
        return messageId != 0;
    }

    /**
     * 校验客户端ID是否合法。
     * <ul>
     *   <li>MQTT 3.1：客户端ID长度必须在 1~23 个字符之间</li>
     *   <li>MQTT 3.1.1：服务器可以允许零长度客户端ID，也允许超过 23 字节的客户端ID</li>
     * </ul>
     *
     * @param mqttVersion MQTT 版本
     * @param clientId    客户端ID
     * @return 合法返回 true
     */
    static boolean isValidClientId(MqttVersion mqttVersion, String clientId) {
        if (mqttVersion == MqttVersion.MQTT_3_1) {
            return clientId != null && clientId.length() >= MIN_CLIENT_ID_LENGTH && clientId.length() <= MAX_CLIENT_ID_LENGTH;
        }
        if (mqttVersion == MqttVersion.MQTT_3_1_1) {
            // In 3.1.3.1 Client Identifier of MQTT 3.1.1 specification, The Server MAY allow ClientId’s
            // that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
            return clientId != null;
        }
        throw new IllegalArgumentException(mqttVersion + " is unknown mqtt version");
    }

    /**
     * 校验固定头部的 QoS 级别。
     * <p>PUBREL、SUBSCRIBE、UNSUBSCRIBE 消息必须使用 QoS 1。</p>
     *
     * @param mqttFixedHeader 待校验的固定头部
     * @return 校验后的固定头部
     * @throws DecoderException QoS 级别不合法
     */
    static MqttFixedHeader validateFixedHeader(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.qosLevel() != MqttQoS.AT_LEAST_ONCE) {
                    throw new DecoderException(mqttFixedHeader.messageType().name() + " message must have QoS 1");
                }
            default:
                return mqttFixedHeader;
        }
    }

    /**
     * 重置不使用标志位的字段。
     * <p>某些消息类型的 DUP、QoS、Retain 标志位不适用，
     * 根据 MQTT 协议规范将其重置为默认值。</p>
     *
     * @param mqttFixedHeader 待处理的固定头部
     * @return 重置后的固定头部（如果无需重置则返回原对象）
     */
    static MqttFixedHeader resetUnusedFields(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
            case CONNACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case SUBACK:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                if (mqttFixedHeader.isDup() ||
                        mqttFixedHeader.qosLevel() != MqttQoS.AT_MOST_ONCE ||
                        mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            false,
                            MqttQoS.AT_MOST_ONCE,
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            mqttFixedHeader.isDup(),
                            mqttFixedHeader.qosLevel(),
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }

    private MqttCodecUtil() {
    }
}
