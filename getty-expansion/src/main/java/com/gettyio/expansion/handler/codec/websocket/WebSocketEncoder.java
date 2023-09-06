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
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.ObjectUtil;
import com.gettyio.expansion.handler.codec.websocket.frame.WebSocketFrame;

/**
 * version 5+
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |Masking-key, if MASK set to 1  |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 * version 1-4
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |M|R|R|R| opcode|R| Payload len |    Extended payload length    |
 * |O|S|S|S|  (4)  |S|     (7)     |             (16/63)           |
 * |R|V|V|V|       |V|             |   (if payload len==126/127)   |
 * |E|1|2|3|       |4|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |         Extension data        |
 * +-------------------------------+ - - - - - - - - - - - - - - - +
 * :                                                               :
 * +---------------------------------------------------------------+
 * :                       Application data                        :
 * +---------------------------------------------------------------+
 */

/**
 * WebSocketEncoder.java
 *
 * @description:http协议响应编码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketEncoder extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (ctx.channel().getChannelAttribute(WebSocketConstants.WEB_SOCKET_HAND_SHAKE) != null && (boolean) ctx.channel().getChannelAttribute(WebSocketConstants.WEB_SOCKET_HAND_SHAKE)) {
            byte[] bytes;
            if (obj instanceof WebSocketFrame) {
                bytes = ((WebSocketFrame) obj).getPayloadData();
                if ((int) ctx.channel().getChannelAttribute(WebSocketConstants.WEB_SOCKET_PROTOCOL_VERSION) <= WebSocketConstants.SPLIT_VERSION0) {
                    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
                    autoByteBuffer.writeBytes(WebSocketConstants.BEGIN_MSG.getBytes(CharsetUtil.UTF_8));
                    autoByteBuffer.writeBytes(bytes);
                    autoByteBuffer.writeBytes(WebSocketConstants.END_MSG.getBytes(CharsetUtil.UTF_8));
                    obj = autoByteBuffer.array();
                } else {
                    obj = codeVersion6(bytes, ((WebSocketFrame) obj).getOpcode());
                }
            } else {
                //如果发送的不是WebSocketFrame，则默认构建二进制WebSocketFrame
                bytes = ObjectUtil.ObjToByteArray(obj);
                obj = codeVersion6(bytes, Opcode.BINARY.getCode());
            }
        }
        super.channelWrite(ctx, obj);
    }


    /**
     * 方法名：codeVersion6
     *
     * @param msg byte[]
     * @return byte[]
     * 对websocket协议进行编码
     */
    public byte[] codeVersion6(byte[] msg, byte op) {

        AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
        WebSocketFrame messageFrame = new WebSocketFrame();
        messageFrame.setPayloadLen(msg.length);

        byte[] headers = new byte[2];
        headers[0] = WebSocketFrame.FIN;
        headers[0] |= messageFrame.getRsv1() | messageFrame.getRsv2() | messageFrame.getRsv3() | op;
        headers[1] = 0;
        headers[1] |= 0x00 | messageFrame.getPayloadLen();
        // 头部控制信息
        autoByteBuffer.writeBytes(headers);
        if (messageFrame.getPayloadLen() == WebSocketFrame.HAS_EXTEND_DATA) {
            // 处理数据长度为126位的情况
            autoByteBuffer.writeBytes(ObjectUtil.shortToByte(messageFrame.getPayloadLenExtended()));
        } else if (messageFrame.getPayloadLen() == WebSocketFrame.HAS_EXTEND_DATA_CONTINUE) {
            // 处理数据长度为127位的情况
            autoByteBuffer.writeBytes(ObjectUtil.longToByte(messageFrame.getPayloadLenExtendedContinued()));
        }

        //写到客户端不需要做掩码处理
//        if (messageFrame.isMask()) {
//            // 做了掩码处理的，需要传递掩码的key
//            byte[] keys = messageFrame.getMaskingKey();
//            autoByteBuffer.writeBytes(messageFrame.getMaskingKey());
//            for (int i = 0; i < autoByteBuffer.array().length; ++i) {
//                //进行掩码处理
//                autoByteBuffer.array()[i] ^= keys[i % 4];
//            }
//        }

        autoByteBuffer.writeBytes(msg);

        return autoByteBuffer.readableBytesArray();
    }


}
