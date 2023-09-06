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
package com.gettyio.expansion.handler.codec.protobuf;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;

import java.io.IOException;


/**
 * ProtobufVarint32FrameDecoder.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {

        byte[] bytes = (byte[]) in;
        autoByteBuffer.writeBytes(bytes);

        while (autoByteBuffer.hasRemaining()) {
            int preIndex = autoByteBuffer.readerIndex();
            int length = readRawVarint32(autoByteBuffer);
            if (preIndex == autoByteBuffer.readerIndex()) {
                return;
            }
            if (length < 0) {
                throw new RuntimeException("negative length: " + length);
            }
            if (autoByteBuffer.readableBytes() < length) {
                autoByteBuffer.readerIndex(0);
                break;
            } else {
                byte[] b = new byte[length];
                autoByteBuffer.readBytes(b);
                autoByteBuffer.discardReadBytes();
                //解码
                super.channelRead(ctx, b);
            }
        }
    }


    /**
     * Reads variable length 32bit int from buffer
     *
     * @return decoded int if buffers readerIndex has been forwarded else nonsense value
     */
    private static int readRawVarint32(AutoByteBuffer buffer) throws IOException {

        if (!buffer.hasRemaining()) {
            return 0;
        }
        //buffer.markReaderIndex();
        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if (!buffer.hasRemaining()) {
                buffer.reset();
                return 0;
            }
            if ((tmp = buffer.readByte()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if (!buffer.hasRemaining()) {
                    buffer.reset();
                    return 0;
                }
                if ((tmp = buffer.readByte()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if (!buffer.hasRemaining()) {
                        buffer.reset();
                        return 0;
                    }
                    if ((tmp = buffer.readByte()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        if (!buffer.hasRemaining()) {
                            buffer.reset();
                            return 0;
                        }
                        result |= (tmp = buffer.readByte()) << 28;
                        if (tmp < 0) {
                            throw new RuntimeException("malformed varint.");
                        }
                    }
                }
            }
            return result;
        }
    }
}
