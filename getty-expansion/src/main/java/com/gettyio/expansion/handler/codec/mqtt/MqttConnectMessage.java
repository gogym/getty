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

/**
 * MQTT CONNECT 消息。
 * <p>客户端发送到服务端的连接请求消息，包含协议版本、连接标志、客户端ID等。</p>
 *
 * @see MqttConnectVariableHeader
 * @see MqttConnectPayload
 */
public final class MqttConnectMessage extends MqttMessage {

    public MqttConnectMessage(MqttFixedHeader mqttFixedHeader, MqttConnectVariableHeader variableHeader, MqttConnectPayload payload) {
        super(mqttFixedHeader, variableHeader, payload);
    }

    @Override
    public MqttConnectVariableHeader variableHeader() {
        return (MqttConnectVariableHeader) super.variableHeader();
    }

    @Override
    public MqttConnectPayload payload() {
        return (MqttConnectPayload) super.payload();
    }
}
