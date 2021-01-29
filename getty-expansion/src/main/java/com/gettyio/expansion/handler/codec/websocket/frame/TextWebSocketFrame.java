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
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.CharsetUtil;

import java.io.UnsupportedEncodingException;

/**
 * Web Socket frame containing binary data.
 */
public class TextWebSocketFrame extends WebSocketFrame {

    protected static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(TextWebSocketFrame.class);

    public TextWebSocketFrame() {
        setOpcode(Opcode.TEXT.getCode());
    }

    public TextWebSocketFrame(String text) {
        setOpcode(Opcode.TEXT.getCode());
        try {
            byte[] bytes = text.getBytes("utf-8");
            setPayloadData(bytes);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e);
        }
    }

    public TextWebSocketFrame(byte[] bytes) {
        setOpcode(Opcode.TEXT.getCode());
        setPayloadData(bytes);
    }

    public String text() {
        return new String(getPayloadData(), CharsetUtil.UTF_8);
    }


}
