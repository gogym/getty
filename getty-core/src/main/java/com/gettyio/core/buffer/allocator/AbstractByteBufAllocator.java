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
package com.gettyio.core.buffer.allocator;


import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.buffer.buffer.EmptyByteBuf;
import com.gettyio.core.util.PlatformDependent;
import com.gettyio.core.util.StringUtil;

/**
 * 缓冲区分配者基类
 * AbstractByteBufAllocator.java
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    /**
     * 默认的初始化容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    /**
     * 是否默认使用堆外内存
     */
    private final boolean directByDefault;
    /**
     * 一个空的缓冲区
     */
    private final ByteBuf emptyBuf;

    /**
     * 实例默认使用堆缓冲区
     */
    protected AbstractByteBufAllocator() {
        this(false);
    }

    /**
     * 创建新实例
     *
     * @param preferDirect {@code true} 是否使用堆外内存
     */
    protected AbstractByteBufAllocator(boolean preferDirect) {
        directByDefault = preferDirect && PlatformDependent.hasUnsafe();
        emptyBuf = new EmptyByteBuf(this);
    }

    @Override
    public ByteBuf buffer() {
        if (directByDefault) {
            return directBuffer();
        }
        return heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        if (PlatformDependent.hasUnsafe()) {
            return directBuffer(DEFAULT_INITIAL_CAPACITY);
        }
        return heapBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        if (PlatformDependent.hasUnsafe()) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        if (PlatformDependent.hasUnsafe()) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return directBuffer(DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newDirectBuffer(initialCapacity, maxCapacity);
    }


    /**
     * 验证初始化大小是否超出最大
     */
    private static void validate(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expectd: 0+)");
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format("initialCapacity: %d (expected: not greater than maxCapacity(%d)", initialCapacity, maxCapacity));
        }
    }

    /**
     * 用给定的initialCapacity和maxCapacity创建一个堆{@link ByteBuf}。
     */
    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 用给定的initialCapacity和maxCapacity创建一个直接的{@link ByteBuf}。
     */
    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(directByDefault: " + directByDefault + ')';
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        if (minNewCapacity < 0) {
            throw new IllegalArgumentException("minNewCapacity: " + minNewCapacity + " (expectd: 0+)");
        }
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format("minNewCapacity: %d (expected: not greater than maxCapacity(%d)", minNewCapacity, maxCapacity));
        }
        final int threshold = 1048576 * 4; // 4 MiB page

        if (minNewCapacity == threshold) {
            return threshold;
        }

        // 如果超过阈值，不要翻倍，而只是增加阈值。
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }

        // 不超过阈值。从64开始，增加到4个MiB。
        int newCapacity = 64;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        return Math.min(newCapacity, maxCapacity);
    }
}
