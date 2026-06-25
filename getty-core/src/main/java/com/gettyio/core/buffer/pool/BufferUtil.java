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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ByteBuffer 工具类，提供缓冲区状态切换、数据读写等核心操作。
 * <p>
 * 仅保留内存池实际使用的方法，精简为缓冲区操作的最小工具集。
 * </p>
 */
public class BufferUtil {

    /**
     * 重置 ByteBuffer 的字节序为大端字节序，并将其位置和限制都设置为 0。
     *
     * @param buffer 需要重置的 ByteBuffer 对象
     */
    public static void reset(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.position(0);
            buffer.limit(0);
        }
    }

    /**
     * 将 ByteBuffer 清空，设置其位置和限制为 0。
     *
     * @param buffer 需要清空的 ByteBuffer 对象
     */
    public static void clear(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.position(0);
            buffer.limit(0);
        }
    }

    /**
     * 将缓冲区切换到填充模式。
     * <p>
     * 如果缓冲区为空（position == limit），重置 position 为 0、limit 为 capacity。
     * 如果缓冲区已满（limit == capacity），执行 compact。
     * 否则将 position 移到 limit 处，limit 设为 capacity。
     * </p>
     *
     * @param buffer 需要切换到填充模式的缓冲区
     * @return 切换前的 position，应传递给后续的 {@link #flipToFlush(ByteBuffer, int)}
     */
    public static int flipToFill(ByteBuffer buffer) {
        int position = buffer.position();
        int limit = buffer.limit();
        if (position == limit) {
            buffer.position(0);
            buffer.limit(buffer.capacity());
            return 0;
        }

        int capacity = buffer.capacity();
        if (limit == capacity) {
            buffer.compact();
            return 0;
        }

        buffer.position(limit);
        buffer.limit(capacity);
        return position;
    }

    /**
     * 将缓冲区切换到刷新模式。
     * limit 设为当前 position，position 设为传入的 position。
     *
     * @param buffer   要被切换的缓冲区
     * @param position 有效数据的起始位置，应为 {@link #flipToFill(ByteBuffer)} 的返回值
     */
    public static void flipToFlush(ByteBuffer buffer, int position) {
        buffer.limit(buffer.position());
        buffer.position(position);
    }

    /**
     * 将缓冲区切换到刷新模式，position 重置为 0。
     *
     * @param buffer 需要进行翻转操作的 ByteBuffer 对象
     */
    public static void flipToFlush(ByteBuffer buffer) {
        flipToFlush(buffer, 0);
    }

    /**
     * 将字节数组追加到指定的 ByteBuffer 中。
     *
     * @param to 目标 ByteBuffer，操作前处于 flush 模式
     * @param b  要追加的字节数组
     * @throws BufferOverflowException 如果空间不足
     */
    public static void append(ByteBuffer to, byte[] b) throws BufferOverflowException {
        append(to, b, 0, b.length);
    }

    /**
     * 将字节数组追加到缓冲区中。
     *
     * @param to  目标 ByteBuffer，操作前处于 flush 模式
     * @param b   要追加的字节数组
     * @param off 字节数组中开始追加的偏移量
     * @param len 要追加的字节长度
     * @throws BufferOverflowException 如果空间不足
     */
    public static void append(ByteBuffer to, byte[] b, int off, int len) throws BufferOverflowException {
        int pos = flipToFill(to);
        try {
            to.put(b, off, len);
        } finally {
            flipToFlush(to, pos);
        }
    }

    /**
     * 将 ByteBuffer 中的数据写入到 byte 数组中。
     *
     * @param buffer 源 ByteBuffer
     * @param out    目标 byte 数组
     */
    public static void writeTo(ByteBuffer buffer, byte[] out) {
        if (buffer == null || out == null) {
            throw new IllegalArgumentException("Buffer and out byte array cannot be null.");
        }

        if (buffer.hasArray()) {
            int position = buffer.position();
            int limit = buffer.limit();

            if (limit - position >= out.length) {
                System.arraycopy(buffer.array(), buffer.arrayOffset() + position, out, 0, out.length);
                buffer.position(position + out.length);
            } else {
                System.arraycopy(buffer.array(), buffer.arrayOffset() + position, out, 0, limit - position);
                buffer.position(limit);
            }
        } else {
            for (int i = 0; i < out.length && buffer.hasRemaining(); i++) {
                out[i] = buffer.get();
            }
        }
    }

    /**
     * 将 ByteBuffer 转换为包含指针和状态信息的详细调试字符串。
     *
     * @param buffer 生成详细字符串的源 ByteBuffer
     * @return 表示 ByteBuffer 状态的字符串
     */
    public static String toDetailString(ByteBuffer buffer) {
        if (buffer == null) {
            return "null";
        }

        StringBuilder buf = new StringBuilder();
        buf.append(buffer.getClass().getSimpleName());
        buf.append("@");
        buf.append(Integer.toHexString(System.identityHashCode(buffer)));
        buf.append("[p=");
        buf.append(buffer.position());
        buf.append(",l=");
        buf.append(buffer.limit());
        buf.append(",c=");
        buf.append(buffer.capacity());
        buf.append(",r=");
        buf.append(buffer.remaining());
        buf.append("]");
        return buf.toString();
    }
}
