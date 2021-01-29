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
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.LinkedNonReadBlockQueue;


/**
 * FixedLengthFrameDecoder.java
 *
 * @description:字符串定长消息解码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class FixedLengthFrameDecoder extends ChannelInboundHandlerAdapter {

    private int frameLength;

    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength must be a positive integer: " + frameLength);
        } else {
            this.frameLength = frameLength;
        }
    }

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {

        byte[] bytes = (byte[]) obj;
        int index = 0;
        while (index < bytes.length) {
            byte[] byte2;
            if ((bytes.length - index) > frameLength) {
                byte2 = new byte[frameLength];
                System.arraycopy(bytes, index, byte2, 0, frameLength);
            } else {
                byte2 = new byte[bytes.length - index];
                System.arraycopy(bytes, index, byte2, 0, bytes.length - index);
            }
            //传递到下一个解码器
            super.decode(socketChannel, obj, out);
            index += frameLength;
        }

    }

}
