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
package com.gettyio.expansion.handler.codec.websocket;


import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 帧操作码（Opcode）。
 * <p>
 * 定义 RFC 6455 中的标准帧类型：
 * <ul>
 *   <li>{@link #CONTINUATION} (0x0) - 续传帧</li>
 *   <li>{@link #TEXT} (0x1) - 文本帧</li>
 *   <li>{@link #BINARY} (0x2) - 二进制帧</li>
 *   <li>{@link #CLOSE} (0x8) - 关闭帧</li>
 *   <li>{@link #PING} (0x9) - Ping 帧</li>
 *   <li>{@link #PONG} (0xA) - Pong 帧</li>
 * </ul>
 * </p>
 *
 * @author gogym
 */
public enum Opcode {

    /** 续传帧（分片消息的后续帧） */
    CONTINUATION((byte) 0),
    /** 文本数据帧 */
    TEXT((byte) 1),
    /** 二进制数据帧 */
    BINARY((byte) 2),
    /** 连接关闭帧 */
    CLOSE((byte) 8),
    /** Ping 心跳帧 */
    PING((byte) 9),
    /** Pong 心跳响应帧 */
    PONG((byte) 10);

    private static final Map<Byte, Opcode> LOOKUP = new HashMap<>();

    static {
        for (Opcode op : values()) {
            LOOKUP.put(op.code, op);
        }
    }

    /**
     * 根据操作码字节值查找对应的枚举值。
     *
     * @param code 4 位操作码
     * @return 对应的 Opcode，未找到时返回 null
     */
    public static Opcode valueOf(byte code) {
        return LOOKUP.get(code);
    }

    private final byte code;

    Opcode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
