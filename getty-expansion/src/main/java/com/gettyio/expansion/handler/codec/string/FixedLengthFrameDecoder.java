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
package com.gettyio.expansion.handler.codec.string;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;

/**
 * 定长帧解码器。
 * <p>
 * 将连续的字节流按固定长度分割为独立的帧。支持半包累积：
 * 当收到的数据不足以组成一个完整帧时，会缓存到下一次数据到达后继续拼接。
 * </p>
 *
 * @author gogym
 */
public class FixedLengthFrameDecoder extends ByteToMessageDecoder {

    private final int frameLength;

    /** 半包累积缓冲区 */
    private final AutoByteBuffer cumulation = AutoByteBuffer.newByteBuffer();

    /**
     * 创建定长帧解码器。
     *
     * @param frameLength 每帧的固定长度（必须为正整数）
     * @throws IllegalArgumentException 如果 frameLength 不是正整数
     */
    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        // 零分配：通过 readArray() 一次性获取底层数组并消费全部可读数据
        int len = buf.readableBytes();
        int offset = buf.readerIndex();
        cumulation.writeBytes(buf.readArray(), offset, len);

        while (cumulation.readableBytes() >= frameLength) {
            byte[] frame = new byte[frameLength];
            cumulation.readBytes(frame);
            cumulation.discardReadBytes();
            super.channelRead(ctx, frame);
        }
    }
}
