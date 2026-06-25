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
package com.gettyio.expansion.handler.codec.websocket.frame;


import com.gettyio.expansion.handler.codec.websocket.Opcode;

/**
 * WebSocket 续传帧。
 * <p>
 * 用于分片消息的后续帧（Opcode = 0x0）。
 * 第一个分片帧指定消息类型（文本/二进制），后续分片使用续传帧。
 * </p>
 *
 * @author gogym
 */
public class ContinuationWebSocketFrame extends WebSocketFrame {


    public ContinuationWebSocketFrame() {
        setOpcode(Opcode.CONTINUATION.getCode());
    }


}
