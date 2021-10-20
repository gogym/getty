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
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.LinkedBlockQueue;
import com.gettyio.expansion.handler.codec.websocket.frame.*;

/**
 * WebSocketDecoder.java
 *
 * @description:websocket解码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketDecoder extends ObjectToMessageDecoder {


    /**
     * 协议版本,默认0
     */
    public int protocolVersion = WebSocketConstants.SPLIT_VERSION0;

    /**
     * 是否已经握手
     */
    public boolean handShake = false;

    /**
     * websocket数据帧
     */
    WebSocketFrame messageFrame;
    /**
     * 用于保存握手信息
     */
    AutoByteBuffer shakeByteBuffer = AutoByteBuffer.newByteBuffer();

    WebSocketRequest requestInfo = new WebSocketRequest();

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedBlockQueue<Object> out) throws Exception {
        if (handShake) {
            // 已经握手处理
            if (protocolVersion >= WebSocketConstants.SPLIT_VERSION6) {
                AutoByteBuffer frameByteBuffer = AutoByteBuffer.newByteBuffer().writeBytes((byte[]) obj);
                //解析数据帧
                WebSocketFrame frame = parserVersion6(frameByteBuffer);
                messageFrame = null;
                if (frame != null) {
                    out.put(frame);
                    super.decode(socketChannel, obj, out);
                }
            } else {
                out.put(obj);
                super.decode(socketChannel, obj, out);
            }
        } else {
            // 进行握手处理
            shakeByteBuffer.writeBytes((byte[]) obj);
            WebSocketHandShake.parserRequest(shakeByteBuffer, requestInfo);
            if (requestInfo.getReadStatus() != WebSocketHandShake.READ_CONTENT) {
                return;
            }
            //写出握手信息到客户端
            byte[] bytes = WebSocketHandShake.generateHandshake(requestInfo, socketChannel).getBytes();
            if (socketChannel.getSslHandler() == null) {
                socketChannel.writeToChannel(bytes);
            } else {
                //需要注意的是，当开启了ssl，握手信息需要经过ssl encode之后才能输出给客户端。
                //为了避免握手信息经过其他的encoder，所以直接指定通过sslHandler输出
                socketChannel.getSslHandler().encode(socketChannel, bytes);
            }
            protocolVersion = requestInfo.getSecVersion();
            handShake = true;
            socketChannel.setChannelAttribute(WebSocketConstants.WEB_SOCKET_HAND_SHAKE, true);
            socketChannel.setChannelAttribute(WebSocketConstants.WEB_SOCKET_PROTOCOL_VERSION, protocolVersion);
            shakeByteBuffer.clear();
        }

    }


    /**
     * 方法名：parser
     *
     * @param buffer
     * @return byte[]
     * 说明：解析版本6以后的数据帧格式
     */
    private WebSocketFrame parserVersion6(AutoByteBuffer buffer) throws Exception {
        do {
            if (messageFrame == null) {
                // 没有出现半包
                //获取opcode
                byte bt = buffer.read(0);
                byte opcode = (byte) (bt & 0x0F);
                if (null == Opcode.valueOf(opcode)) {
                    return null;
                }
                //按类型构建帧
                switch (Opcode.valueOf(opcode)) {
                    case CONTINUATION:
                        messageFrame = new ContinuationWebSocketFrame();
                        break;
                    case TEXT:
                        messageFrame = new TextWebSocketFrame();
                        break;
                    case BINARY:
                        messageFrame = new BinaryWebSocketFrame();
                        break;
                    case CLOSE:
                        messageFrame = new CloseWebSocketFrame();
                        break;
                    case PING:
                        messageFrame = new PingWebSocketFrame();
                        break;
                    case PONG:
                        messageFrame = new PongWebSocketFrame();
                        break;
                    default:
                        return null;
                }
            }
            if (!messageFrame.isReadFinish()) {
                // 读取解析消息头
                messageFrame.parseMessageHeader(buffer);
            }
            int bufferDataLength = buffer.readableBytes();
            int dataLength = bufferDataLength > messageFrame.getDateLength() ? new Long(messageFrame.getDateLength()).intValue() : bufferDataLength;
            byte[] bytes = new byte[dataLength];
            if (dataLength > 0) {
                buffer.readBytes(bytes);
                if (messageFrame.isMask()) {
                    // 做加密处理
                    for (int i = 0; i < dataLength; i++) {
                        bytes[i] ^= messageFrame.getMaskingKey()[(i % 4)];
                    }
                }
                messageFrame.setPayloadData(bytes);
            }

            if (messageFrame.isReadFinish()) {
                return messageFrame;
            }
        } while (buffer.hasRemaining());
        return null;
    }


    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        handShake = false;
        protocolVersion = 0;
        messageFrame = null;
        shakeByteBuffer.clear();
        requestInfo = new WebSocketRequest();
        super.channelClosed(socketChannel);
    }
}
