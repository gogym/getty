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

/**
 * MQTT 消息类型枚举。
 * <p>定义 MQTT 协议支持的全部 14 种控制报文类型。</p>
 *
 * @see <a href="http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718021">MQTT v3.1.1 - Message Types</a>
 */
public enum MqttMessageType {
    /** 客户端请求连接 */
    CONNECT(1),
    /** 服务端确认连接 */
    CONNACK(2),
    /** 发布消息 */
    PUBLISH(3),
    /** 发布确认（QoS 1） */
    PUBACK(4),
    /** 发布接收（QoS 2 第一步） */
    PUBREC(5),
    /** 发布释放（QoS 2 第二步） */
    PUBREL(6),
    /** 发布完成（QoS 2 第三步） */
    PUBCOMP(7),
    /** 客户端订阅请求 */
    SUBSCRIBE(8),
    /** 服务端订阅确认 */
    SUBACK(9),
    /** 客户端取消订阅 */
    UNSUBSCRIBE(10),
    /** 服务端取消订阅确认 */
    UNSUBACK(11),
    /** 心跳请求 */
    PINGREQ(12),
    /** 心跳响应 */
    PINGRESP(13),
    /** 客户端断开连接 */
    DISCONNECT(14);

    private final int value;

    /** 按消息类型值索引的查找表，实现 O(1) 查找 */
    private static final MqttMessageType[] LOOKUP = new MqttMessageType[15];

    static {
        for (MqttMessageType t : values()) {
            LOOKUP[t.value] = t;
        }
    }

    MqttMessageType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /**
     * 根据消息类型值获取对应的枚举实例
     *
     * @param type 消息类型值 (1~14)
     * @return 对应的消息类型枚举
     * @throws IllegalArgumentException 未知的消息类型
     */
    public static MqttMessageType valueOf(int type) {
        if (type >= 1 && type < LOOKUP.length && LOOKUP[type] != null) {
            return LOOKUP[type];
        }
        throw new IllegalArgumentException("unknown message type: " + type);
    }
}

