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


import com.gettyio.core.util.StringUtil;

/**
 * MQTT CONNECT 消息的负载部分。
 * <p>包含客户端标识、遗嘱主题/消息、用户名和密码。</p>
 *
 * @see MqttConnectMessage
 */
public final class MqttConnectPayload {

    private final String clientIdentifier;
    private final String willTopic;
    private final byte[] willMessage;
    private final String userName;
    private final byte[] password;

    /**
     * 创建 CONNECT 负载
     *
     * @param clientIdentifier 客户端标识符
     * @param willTopic        遗嘱主题（可选，无遗嘱时为 null）
     * @param willMessage      遗嘱消息字节数组（可选，无遗嘱时为 null）
     * @param userName         用户名（可选）
     * @param password         密码字节数组（可选）
     */
    public MqttConnectPayload(String clientIdentifier, String willTopic, byte[] willMessage, String userName, byte[] password) {
        this.clientIdentifier = clientIdentifier;
        this.willTopic = willTopic;
        this.willMessage = willMessage;
        this.userName = userName;
        this.password = password;
    }

    /**
     * 获取客户端标识符
     *
     * @return 客户端标识符
     */
    public String clientIdentifier() {
        return clientIdentifier;
    }

    /**
     * 获取遗嘱主题
     *
     * @return 遗嘱主题，无遗嘱时返回 null
     */
    public String willTopic() {
        return willTopic;
    }

    /**
     * 获取遗嘱消息字节数组
     *
     * @return 遗嘱消息字节数组，无遗嘱时返回 null
     */
    public byte[] willMessageInBytes() {
        return willMessage;
    }

    /**
     * 获取用户名
     *
     * @return 用户名，未设置时返回 null
     */
    public String userName() {
        return userName;
    }

    /**
     * 获取密码字节数组
     *
     * @return 密码字节数组，未设置时返回 null
     */
    public byte[] passwordInBytes() {
        return password;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("clientIdentifier=").append(clientIdentifier)
                .append(", willTopic=").append(willTopic)
                .append(", willMessage=").append(willMessage)
                .append(", userName=").append(userName)
                .append(", password=").append(password)
                .append(']')
                .toString();
    }
}
