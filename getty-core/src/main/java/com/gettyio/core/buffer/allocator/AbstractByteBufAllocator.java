/*
 * Copyright 2019 The Getty Project
 * <p>
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.buffer.allocator;

import com.gettyio.core.buffer.bytebuf.ByteBuf;

/**
 * 缓冲区分配者基类
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    /**
     * 默认的初始化容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * 创建新实例
     */
    protected AbstractByteBufAllocator() {
    }

    @Override
    public ByteBuf buffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return heapBuffer(initialCapacity, Integer.MAX_VALUE);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return heapBuffer(initialCapacity, maxCapacity);
    }

    private ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        validate(initialCapacity, maxCapacity);
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * 验证初始化大小是否超出最大
     */
    private static void validate(int initialCapacity, int maxCapacity) {
        if (initialCapacity <= 0) {
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

}
