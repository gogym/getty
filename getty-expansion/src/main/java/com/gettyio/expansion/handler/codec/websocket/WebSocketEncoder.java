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

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.ObjectUtil;
import com.gettyio.expansion.handler.codec.websocket.frame.WebSocketFrame;

/**
 * WebSocket 帧编码器。
 * <p>
 * 将 {@link WebSocketFrame} 或其他对象编码为符合 RFC 6455 的 WebSocket 帧格式。
 * 服务端发送的帧不做掩码处理（仅客户端到服务端的帧需要掩码）。
 * </p>
 *
 * <pre>
 * RFC 6455 帧格式：
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |     Extended payload length continued, if payload len == 127  |
 * +---------------------------------------------------------------+
 * |                               |          Payload Data         |
 * +---------------------------------------------------------------+
 * </pre>
 *
 * @author gogym
 */
public class WebSocketEncoder extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        Object handshakeAttr = ctx.channel().getChannelAttribute(WebSocketConstants.WEB_SOCKET_HAND_SHAKE);
        if (handshakeAttr != null && (boolean) handshakeAttr) {
            byte[] encoded;
            if (obj instanceof WebSocketFrame) {
                WebSocketFrame frame = (WebSocketFrame) obj;
                byte[] payload = frame.getPayloadData();
                int version = (int) ctx.channel().getChannelAttribute(WebSocketConstants.WEB_SOCKET_PROTOCOL_VERSION);
                if (version <= WebSocketConstants.SPLIT_VERSION0) {
                    // Hixie-76 格式：\u0000 + payload + \u00FF
                    AutoByteBuffer buf = AutoByteBuffer.newByteBuffer();
                    buf.writeBytes(WebSocketConstants.BEGIN_MSG.getBytes(CharsetUtil.UTF_8));
                    buf.writeBytes(payload);
                    buf.writeBytes(WebSocketConstants.END_MSG.getBytes(CharsetUtil.UTF_8));
                    encoded = buf.array();
                } else {
                    encoded = encodeFrame(payload, frame.getOpcode());
                }
            } else {
                // 非 WebSocketFrame 对象，默认构建二进制帧
                byte[] payload = ObjectUtil.ObjToByteArray(obj);
                encoded = encodeFrame(payload, Opcode.BINARY.getCode());
            }
            PooledByteBuffer buf = ctx.channel().getByteBufferPool().acquire(encoded.length);
            buf.writeBytes(encoded);
            obj = buf;
        }
        super.channelWrite(ctx, obj);
    }

    /**
     * 将负载数据编码为 RFC 6455 WebSocket 帧。
     * <p>
     * 服务端发送的帧不做掩码处理（MASK=0）。
     * 直接计算精确大小分配 byte[]，避免 AutoByteBuffer 扩容开销。
     * </p>
     *
     * @param payload 负载数据
     * @param opcode  操作码（文本、二进制等）
     * @return 编码后的完整帧字节数组
     */
    private static byte[] encodeFrame(byte[] payload, byte opcode) {
        int len = payload.length;
        int headerLen;
        int extLen;

        // 计算帧头大小
        if (len < 126) {
            headerLen = 2;
            extLen = 0;
        } else if (len <= 0xFFFF) {
            headerLen = 2;
            extLen = 2;
        } else {
            headerLen = 2;
            extLen = 8;
        }

        // 精确分配，一次到位
        byte[] frame = new byte[headerLen + extLen + len];
        // 第 1 字节：FIN=1 + RSV=0 + opcode
        frame[0] = (byte) (WebSocketFrame.FIN | opcode);

        int offset = headerLen;
        // 第 2 字节及后续：MASK=0 + payload length
        if (len < 126) {
            frame[1] = (byte) len;
        } else if (len <= 0xFFFF) {
            // 16 位扩展长度
            frame[1] = (byte) 126;
            frame[2] = (byte) (len >> 8);
            frame[3] = (byte) len;
        } else {
            // 64 位扩展长度
            frame[1] = (byte) 127;
            for (int i = 56; i >= 0; i -= 8) {
                frame[offset++] = (byte) (len >> i);
            }
        }

        System.arraycopy(payload, 0, frame, headerLen + extLen, len);
        return frame;
    }
}
