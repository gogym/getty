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
import com.gettyio.core.buffer.AutoByteBuffer.ByteBufferException;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;

/**
 * Protobuf Varint32 帧解码器。
 * <p>
 * 使用 Protobuf 的 Varint32 编码作为帧长度字段，将连续的字节流分割为独立的消息帧。
 * 支持半包累积：当数据不足以组成一个完整帧时，缓存到下一次数据到达。
 * </p>
 *
 * @author gogym
 * @see ProtobufVarint32LengthFieldPrepender
 */
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    /** 半包累积缓冲区 */
    private final AutoByteBuffer cumulation = AutoByteBuffer.newByteBuffer();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        // 零分配：通过 readArray() 直接拷贝到 cumulation，跳过中间 byte[]
        int len = buf.readableBytes();
        int offset = buf.arrayOffset();
        cumulation.writeBytes(buf.readArray(), offset, len);

        while (cumulation.hasRemaining()) {
            int preIndex = cumulation.readerIndex();
            int length = readRawVarint32(cumulation);

            // readerIndex 未推进，说明 varint 数据不足
            if (preIndex == cumulation.readerIndex()) {
                return;
            }
            if (length < 0) {
                throw new RuntimeException("negative length: " + length);
            }
            if (length == 0) {
                cumulation.discardReadBytes();
                continue;
            }
            if (cumulation.readableBytes() < length) {
                // 数据不足以组成一帧，整体回退等待更多数据
                cumulation.readerIndex(preIndex);
                break;
            }

            byte[] frame = new byte[length];
            cumulation.readBytes(frame);
            super.channelRead(ctx, frame);
        }
        cumulation.discardReadBytes();
    }

    /**
     * 从缓冲区读取 Protobuf Varint32 编码的整数。
     * <p>
     * 数据不足时回退 readerIndex 到调用前的位置并返回 0，
     * 调用方通过 {@code preIndex == readerIndex} 判断数据不足。
     * </p>
     *
     * @param buffer 数据缓冲区
     * @return 解码后的整数值；数据不足时返回 0 且 readerIndex 不变
     * @throws RuntimeException 如果 Varint 格式不合法
     * @throws ByteBufferException 缓冲区读取异常
     */
    private static int readRawVarint32(AutoByteBuffer buffer) throws ByteBufferException {
        if (!buffer.hasRemaining()) {
            return 0;
        }

        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        }

        int result = tmp & 0x7F;
        if (!buffer.hasRemaining()) { buffer.readerIndex(buffer.readerIndex() - 1); return 0; }
        if ((tmp = buffer.readByte()) >= 0) { result |= tmp << 7; }
        else {
            result |= (tmp & 0x7F) << 7;
            if (!buffer.hasRemaining()) { buffer.readerIndex(buffer.readerIndex() - 2); return 0; }
            if ((tmp = buffer.readByte()) >= 0) { result |= tmp << 14; }
            else {
                result |= (tmp & 0x7F) << 14;
                if (!buffer.hasRemaining()) { buffer.readerIndex(buffer.readerIndex() - 3); return 0; }
                if ((tmp = buffer.readByte()) >= 0) { result |= tmp << 21; }
                else {
                    result |= (tmp & 0x7F) << 21;
                    if (!buffer.hasRemaining()) { buffer.readerIndex(buffer.readerIndex() - 4); return 0; }
                    result |= (tmp = buffer.readByte()) << 28;
                    if (tmp < 0) {
                        throw new RuntimeException("malformed varint");
                    }
                }
            }
        }
        return result;
    }
}
