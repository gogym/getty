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
package com.gettyio.core.buffer.pool;

import java.nio.ByteBuffer;

/**
 * ByteBuffer 包装类，提供缓冲区状态切换和数据读写的便捷方法。
 * <p>
 * 该类是 {@link PooledByteBuffer} 的基类，封装了底层 ByteBuffer，
 * 提供 flipToFill/flipToFlush 模式切换以及 put/get 数据操作。
 * 子类（PooledByteBuffer）负责实现引用计数和池化归还逻辑。
 * </p>
 */
public class RetainableByteBuffer {

    /**
     * 底层 ByteBuffer 实例
     */
    private final ByteBuffer buffer;

    /**
     * 构造 RetainableByteBuffer。
     *
     * @param buffer   底层 ByteBuffer
     * @param releaser 保留参数，兼容子类构造调用，当前未使用
     */
    RetainableByteBuffer(ByteBuffer buffer, java.util.function.Consumer<RetainableByteBuffer> releaser) {
        this.buffer = buffer;
    }

    /**
     * 获取底层的 ByteBuffer 对象。
     *
     * @return ByteBuffer 对象
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * 返回缓冲区中剩余的字节数。
     *
     * @return 剩余字节数
     */
    public int remaining() {
        return buffer.remaining();
    }

    /**
     * 检查缓冲区是否还有剩余数据。
     *
     * @return 如果还有数据未处理，则返回 true；否则返回 false
     */
    public boolean hasRemaining() {
        return buffer.remaining() > 0;
    }

    /**
     * 往缓冲区追加数据。
     *
     * @param bytes 要写入的字节数组
     */
    public void put(byte[] bytes) {
        BufferUtil.append(buffer, bytes);
    }

    /**
     * 从缓冲区读取数据到字节数组。
     *
     * @param bytes 目标字节数组
     */
    public void get(byte[] bytes) {
        BufferUtil.writeTo(buffer, bytes);
    }

    /**
     * 将缓冲区切换到填充模式。
     *
     * @return 切换前的 position，用于后续 flipToFlush 恢复
     */
    public ByteBuffer flipToFill() {
        BufferUtil.flipToFill(buffer);
        return buffer;
    }

    /**
     * 将缓冲区切换到刷新模式。
     */
    public void flipToFlush() {
        BufferUtil.flipToFlush(buffer);
    }

    /**
     * 释放缓冲区。子类（PooledByteBuffer）重写此方法实现池化归还。
     *
     * @return true 如果缓冲区已释放
     */
    public boolean release() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s@%x{%s}",
                getClass().getSimpleName(), hashCode(), BufferUtil.toDetailString(buffer));
    }
}
