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
 * Opcode.java
 *
 * @description:消息类型
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public enum Opcode {

    /**
     * 附加数据帧、文本数据帧、二进制数据帧、连接关闭、ping、pong
     */
    CONTINUATION((byte) 0), TEXT((byte) 1), BINARY((byte) 2), CLOSE((byte) 8), PING((byte) 9), PONG((byte) 10);

    private static Map<Byte, Opcode> map = new HashMap<>();

    static {
        for (Opcode opcode : values()) {
            map.put(opcode.getCode(), opcode);
        }
    }

    public static Opcode valueOf(byte code) {
        return map.get(code);
    }

    private byte code;

    private Opcode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

}
