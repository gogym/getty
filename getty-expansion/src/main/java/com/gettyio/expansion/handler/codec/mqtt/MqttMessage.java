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


import com.gettyio.core.handler.codec.DecoderResult;
import com.gettyio.core.util.StringUtil;

/**
 * MQTT 消息基类。
 * <p>所有 MQTT 消息类型的父类，包含固定头部、可变头部和负载三个部分。</p>
 *
 * @see MqttFixedHeader
 */
public class MqttMessage {

    private final MqttFixedHeader mqttFixedHeader;
    private final Object variableHeader;
    private final Object payload;
    private final DecoderResult decoderResult;

    /**
     * 创建仅含固定头部的 MQTT 消息
     *
     * @param mqttFixedHeader 固定头部
     */
    public MqttMessage(MqttFixedHeader mqttFixedHeader) {
        this(mqttFixedHeader, null, null);
    }

    /**
     * 创建含固定头部和可变头部的 MQTT 消息
     *
     * @param mqttFixedHeader 固定头部
     * @param variableHeader  可变头部
     */
    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader) {
        this(mqttFixedHeader, variableHeader, null);
    }

    /**
     * 创建含固定头部、可变头部和负载的 MQTT 消息
     *
     * @param mqttFixedHeader 固定头部
     * @param variableHeader  可变头部
     * @param payload         负载
     */
    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        this(mqttFixedHeader, variableHeader, payload, DecoderResult.SUCCESS);
    }

    /**
     * 创建完整的 MQTT 消息（包含解码结果）
     *
     * @param mqttFixedHeader 固定头部
     * @param variableHeader  可变头部
     * @param payload         负载
     * @param decoderResult   解码结果
     */
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

    /** 获取固定头部 */
    public MqttFixedHeader fixedHeader() {
        return mqttFixedHeader;
    }

    /** 获取可变头部 */
    public Object variableHeader() {
        return variableHeader;
    }

    /** 获取负载 */
    public Object payload() {
        return payload;
    }

    /** 获取解码结果 */
    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("fixedHeader=").append(fixedHeader() != null ? fixedHeader().toString() : "")
            .append(", variableHeader=").append(variableHeader() != null ? variableHeader.toString() : "")
            .append(", payload=").append(payload() != null ? payload.toString() : "")
            .append(']')
            .toString();
    }
}
