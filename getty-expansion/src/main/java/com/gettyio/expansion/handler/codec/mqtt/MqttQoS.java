/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.gettyio.expansion.handler.codec.mqtt;

/**
 * MQTT QoS（服务质量）级别枚举。
 * <p>定义了 MQTT 协议支持的 QoS 级别：0、1、2 以及失败标志。</p>
 */
public enum MqttQoS {
    /** QoS 0：最多一次传递（“即发即忘”） */
    AT_MOST_ONCE(0),
    /** QoS 1：至少一次传递（确认传递） */
    AT_LEAST_ONCE(1),
    /** QoS 2：恰好一次传递（保证传递） */
    EXACTLY_ONCE(2),
    /** SUBACK 中订阅失败标志（0x80） */
    FAILURE(0x80);

    private final int value;

    MqttQoS(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /**
     * 根据 QoS 值获取对应的枚举实例
     *
     * @param value QoS 值 (0、1、2 或 0x80)
     * @return 对应的 QoS 枚举
     * @throws IllegalArgumentException 无效的 QoS 值
     */
    public static MqttQoS valueOf(int value) {
        switch (value) {
            case 0: return AT_MOST_ONCE;
            case 1: return AT_LEAST_ONCE;
            case 2: return EXACTLY_ONCE;
            case 0x80: return FAILURE;
            default: throw new IllegalArgumentException("invalid QoS: " + value);
        }
    }
}
