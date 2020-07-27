/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.handler.codec.websocket;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.LinkedNonReadBlockQueue;
import com.gettyio.core.util.ObjectUtil;

import java.util.Arrays;

/**
 * WebSocketDecoder.java
 *
 * @description:websocket解码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketDecoder extends ObjectToMessageDecoder {

    protected static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(WebSocketDecoder.class);
    /**
     * 是否已经握手
     */
    static boolean handShak = false;
    /**
     * 协议版本,默认0
     */
    public static String protocolVersion = "0";

    WebSocketFrame messageFrame;

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {
        if (handShak) {
            // 已经握手处理
            if (Integer.valueOf(protocolVersion) >= WebSocketConstants.SPLITVERSION6) {
                AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer().writeBytes((byte[]) obj);
                //解析数据帧
                WebSocketFrame frame = parserVersion6(autoByteBuffer);
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
            String msg = new String((byte[]) obj, CharsetUtil.UTF_8);
            WebSocketRequest requestInfo = WebSocketHandShak.parserRequest(msg);
            //写出握手信息到客户端
            byte[] bytes = WebSocketHandShak.generateHandshake(requestInfo, socketChannel).getBytes();
            if (socketChannel.getSslHandler() == null) {
                socketChannel.writeToChannel(bytes);
            } else {
                //需要注意的是，当开启了ssl，握手信息需要经过ssl encode之后才能输出给客户端。
                //为了避免握手信息经过其他的encoder，所以直接指定通过sslHandler输出
                socketChannel.getSslHandler().encode(socketChannel, bytes);
            }
            protocolVersion = requestInfo.getSecVersion().toString();
            handShak = true;
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
                messageFrame = new WebSocketFrame();
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
                //bytes = messageFrame.getPayloadData().readableBytesArray();
                //messageFrame = null;
                return messageFrame;
            }

        } while (buffer.hasRemaining());
        return null;
    }


    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        handShak = false;
        protocolVersion = "0";
        super.channelClosed(socketChannel);
    }
}
