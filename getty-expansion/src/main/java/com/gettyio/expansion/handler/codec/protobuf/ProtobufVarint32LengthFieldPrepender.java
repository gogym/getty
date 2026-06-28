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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;

/**
 * Protobuf Varint32 长度字段前缀编码器。
 * <p>
 * 在消息体前添加 Varint32 编码的长度字段，用于接收端的帧分割。
 * 直接操作 byte[] 避免不必要的 {@link com.gettyio.core.buffer.AutoByteBuffer} 包装。
 * </p>
 *
 * @author gogym
 * @see ProtobufVarint32FrameDecoder
 */
public class ProtobufVarint32LengthFieldPrepender extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        PooledByteBuffer input = (PooledByteBuffer) obj;
        int bodyLen = input.readableBytes();
        int offset = input.arrayOffset();
        // 零分配：通过 readArray() 获取底层数组，跳过中间 byte[] 拷贝
        byte[] body = input.readArray();
        int headerLen = computeRawVarint32Size(bodyLen);
        PooledByteBuffer output = ctx.channel().getByteBufferPool().acquire(headerLen + bodyLen);

        // 写入 Varint32 长度前缀
        int value = bodyLen;
        while (true) {
            if ((value & ~0x7F) == 0) {
                output.writeByte((byte) value);
                break;
            } else {
                output.writeByte((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
        // 写入消息体
        output.writeBytes(body, offset, bodyLen);

        super.channelWrite(ctx, output);
    }

    /**
     * 计算 Varint32 编码后的字节数。
     *
     * @param value 待编码的值
     * @return 编码后占用的字节数（1-5）
     */
    static int computeRawVarint32Size(final int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) return 1;
        if ((value & (0xFFFFFFFF << 14)) == 0) return 2;
        if ((value & (0xFFFFFFFF << 21)) == 0) return 3;
        if ((value & (0xFFFFFFFF << 28)) == 0) return 4;
        return 5;
    }
}
