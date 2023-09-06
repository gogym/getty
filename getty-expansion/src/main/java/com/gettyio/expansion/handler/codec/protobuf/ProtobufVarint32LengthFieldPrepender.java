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
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;


/**
 * ProtobufVarint32LengthFieldPrepender.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class ProtobufVarint32LengthFieldPrepender extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        byte[] bytes = (byte[]) obj;

        int bodyLen = bytes.length;
        int headerLen = computeRawVarint32Size(bodyLen);
        byte[] b = new byte[headerLen + bodyLen];

        AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer(bodyLen);
        writeRawVarint32(autoByteBuffer, bodyLen);
        autoByteBuffer.writeBytes(bytes);
        try {
            autoByteBuffer.readBytes(b);
        } catch (AutoByteBuffer.ByteBufferException e) {
            e.printStackTrace();
        }
        super.channelWrite(ctx, b);
    }


    /**
     * Writes protobuf varint32 to (@link ByteBuf).
     *
     * @param out   to be written to
     * @param value to be written
     */
    static void writeRawVarint32(AutoByteBuffer out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            } else {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }


    /**
     * Computes size of protobuf varint32 after encoding.
     *
     * @param value which is to be encoded.
     * @return size of value encoded as protobuf varint32.
     */
    static int computeRawVarint32Size(final int value) {
        if ((value & (0xffffffff << 7)) == 0) {
            return 1;
        }
        if ((value & (0xffffffff << 14)) == 0) {
            return 2;
        }
        if ((value & (0xffffffff << 21)) == 0) {
            return 3;
        }
        if ((value & (0xffffffff << 28)) == 0) {
            return 4;
        }
        return 5;
    }
}
