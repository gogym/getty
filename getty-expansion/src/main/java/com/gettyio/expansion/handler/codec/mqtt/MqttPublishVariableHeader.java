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


import com.gettyio.core.util.ObjectUtil;

/**
 * MQTT PUBLISH 消息的可变头部。
 * <p>包含主题名称和报文ID（QoS > 0 时存在）。</p>
 *
 * @see MqttPublishMessage
 */
public final class MqttPublishVariableHeader {

    private final String topicName;
    private final int packetId;

    public MqttPublishVariableHeader(String topicName, int packetId) {
        this.topicName = topicName;
        this.packetId = packetId;
    }

    public String topicName() {
        return topicName;
    }

    /**
     * 获取报文ID
     *
     * @return 报文ID，QoS 0 时为 -1
     */
    public int packetId() {
        return packetId;
    }

    @Override
    public String toString() {
        return new StringBuilder(ObjectUtil.simpleClassName(this))
            .append('[')
            .append("topicName=").append(topicName)
            .append(", packetId=").append(packetId)
            .append(']')
            .toString();
    }
}
