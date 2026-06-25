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
 * WebSocket 关闭帧。
 * <p>
 * 用于通知对端关闭连接（Opcode = 0x8）。
 * 收到此帧后应停止发送数据并关闭连接。
 * </p>
 *
 * @author gogym
 */
public class CloseWebSocketFrame extends WebSocketFrame {

    public CloseWebSocketFrame() {
        setOpcode(Opcode.CLOSE.getCode());
    }

}
