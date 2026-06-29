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


import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.ObjectUtil;

/**
 * MQTT 协议版本枚举。
 * <p>支持 MQTT v3.1 和 v3.1.1 两个版本，每个版本定义了协议名称和协议级别。</p>
 */
public enum MqttVersion {
    /** MQTT v3.1，协议名称 "MQIsdp"，级别 3 */
    MQTT_3_1("MQIsdp", (byte) 3),
    /** MQTT v3.1.1，协议名称 "MQTT"，级别 4 */
    MQTT_3_1_1("MQTT", (byte) 4);

    private final String name;
    private final byte level;
    private final byte[] nameBytes;

    MqttVersion(String protocolName, byte protocolLevel) {
        name = ObjectUtil.checkNotNull(protocolName, "protocolName");
        level = protocolLevel;
        nameBytes = protocolName.getBytes(CharsetUtil.UTF_8);
    }

    /**
     * 获取协议名称
     *
     * @return 协议名称字符串
     */
    public String protocolName() {
        return name;
    }

    /**
     * 获取协议名称的 UTF-8 字节数组
     *
     * @return 协议名称字节数组
     */
    public byte[] protocolNameBytes() {
        return nameBytes;
    }

    /**
     * 获取协议级别
     *
     * @return 协议级别字节值
     */
    public byte protocolLevel() {
        return level;
    }

    /**
     * 根据协议名称和协议级别获取对应的 MQTT 版本
     *
     * @param protocolName  协议名称
     * @param protocolLevel 协议级别
     * @return 对应的 MQTT 版本
     * @throws MqttUnacceptableProtocolVersionException 协议名称或级别不匹配
     */
    public static MqttVersion fromProtocolNameAndLevel(String protocolName, byte protocolLevel) {
        for (MqttVersion mv : values()) {
            if (mv.name.equals(protocolName)) {
                if (mv.level == protocolLevel) {
                    return mv;
                } else {
                    throw new IllegalArgumentException(
                            protocolName + " and " + protocolLevel + " do not match");
                }
            }
        }
        throw new IllegalArgumentException(protocolName + " is unknown protocol name");
    }
}
