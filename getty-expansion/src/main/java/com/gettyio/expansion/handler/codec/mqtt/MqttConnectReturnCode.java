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
 * MQTT CONNACK 返回码枚举。
 *
 * @see MqttConnAckMessage
 */
public enum MqttConnectReturnCode {
    /** 连接已接受 */
    CONNECTION_ACCEPTED((byte) 0x00),
    /** 连接被拒绝：不可接受的协议版本 */
    CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION((byte) 0x01),
    /** 连接被拒绝：客户端标识被拒绝 */
    CONNECTION_REFUSED_IDENTIFIER_REJECTED((byte) 0x02),
    /** 连接被拒绝：服务器不可用 */
    CONNECTION_REFUSED_SERVER_UNAVAILABLE((byte) 0x03),
    /** 连接被拒绝：用户名或密码错误 */
    CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD((byte) 0x04),
    /** 连接被拒绝：未授权 */
    CONNECTION_REFUSED_NOT_AUTHORIZED((byte) 0x05);

    private final byte byteValue;

    /** 按返回码值索引的查找表（0~5） */
    private static final MqttConnectReturnCode[] LOOKUP = new MqttConnectReturnCode[6];

    static {
        for (MqttConnectReturnCode code : values()) {
            LOOKUP[code.byteValue] = code;
        }
    }

    MqttConnectReturnCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    public byte byteValue() {
        return byteValue;
    }

    /**
     * 根据字节值获取对应的返回码枚举
     *
     * @param b 返回码字节值 (0~5)
     * @return 对应的返回码枚举
     * @throws IllegalArgumentException 未知的返回码
     */
    public static MqttConnectReturnCode valueOf(byte b) {
        int idx = b & 0xFF;
        if (idx < LOOKUP.length && LOOKUP[idx] != null) {
            return LOOKUP[idx];
        }
        throw new IllegalArgumentException("unknown connect return code: " + idx);
    }
}
