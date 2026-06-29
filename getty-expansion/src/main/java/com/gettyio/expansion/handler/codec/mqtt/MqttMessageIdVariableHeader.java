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
 * MQTT 可变头部（仅包含消息ID）。
 * <p>用于 PUBACK、PUBREC、PUBREL、PUBCOMP、SUBACK、UNSUBACK 等仅含消息ID的消息类型。</p>
 *
 * @see <a href="http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718053">MQTT v3.1.1 - Message ID</a>
 */
public final class MqttMessageIdVariableHeader {

    private final int messageId;

    public static MqttMessageIdVariableHeader from(int messageId) {
      if (messageId < 1 || messageId > 0xffff) {
        throw new IllegalArgumentException("messageId: " + messageId + " (expected: 1 ~ 65535)");
      }
      return new MqttMessageIdVariableHeader(messageId);
    }

    private MqttMessageIdVariableHeader(int messageId) {
        this.messageId = messageId;
    }

    public int messageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return new StringBuilder(ObjectUtil.simpleClassName(this))
            .append('[')
            .append("messageId=").append(messageId)
            .append(']')
            .toString();
    }
}
