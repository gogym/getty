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
 * 分隔符帧解码器。
 * <p>
 * 按指定的分隔符（delimiter）将连续的字节流分割为独立的帧。
 * 支持多字节分隔符，能正确处理跨数据包的分隔符匹配和半包累积。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 *   // 以 \r\n 为分隔符
 *   new DelimiterFrameDecoder(new byte[]{'\r', '\n'});
 * </pre>
 * </p>
 *
 * @author gogym
 * @see StringDecoder
 */
public class DelimiterFrameDecoder extends ByteToMessageDecoder {

    /** 默认分隔符：\r\n */
    public static final byte[] LINE_DELIMITER = {'\r', '\n'};

    /** 累积缓冲区 */
    private final AutoByteBuffer preBuffer = AutoByteBuffer.newByteBuffer();

    /** 分隔符字节数组 */
    private final byte[] delimiter;

    /** 当前分隔符匹配进度 */
    private int matchIndex;

    /**
     * 创建分隔符帧解码器。
     *
     * @param delimiter 分隔符字节数组
     */
    public DelimiterFrameDecoder(byte[] delimiter) {
        if (delimiter == null || delimiter.length == 0) {
            throw new IllegalArgumentException("delimiter must not be null or empty");
        }
        this.delimiter = delimiter;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        // 零分配：通过 readArray() 一次性获取底层数组并消费全部可读数据
        int len = buf.readableBytes();
        int offset = buf.readerIndex();
        preBuffer.writeBytes(buf.readArray(), offset, len);

        // 锁定原始数据边界：后续 writeByte 回写分隔符字节会增大 readableBytes，
        // 必须用固定值防止循环越界。分隔符匹配后 clear 时需重新计算。
        int dataLen = preBuffer.readableBytes();
        int index = 0;
        while (index < dataLen) {
            byte data = preBuffer.array()[preBuffer.readerIndex() + index];
            if (data == delimiter[matchIndex]) {
                matchIndex++;
                if (matchIndex == delimiter.length) {
                    // 完整分隔符匹配成功，输出当前帧
                    super.channelRead(ctx, preBuffer.allWriteBytesArray());
                    preBuffer.clear();
                    matchIndex = 0;
                    // clear 后原始数据已清空，重新计算数据边界
                    dataLen = preBuffer.readableBytes();
                    index = 0;
                }
            } else {
                // 匹配失败：将之前已匹配的分隔符字节写入缓冲区
                if (matchIndex > 0) {
                    for (int i = 0; i < matchIndex; i++) {
                        preBuffer.writeByte(delimiter[i]);
                    }
                    matchIndex = 0;
                }
                preBuffer.writeByte(data);
            }
            index++;
        }
    }
}
