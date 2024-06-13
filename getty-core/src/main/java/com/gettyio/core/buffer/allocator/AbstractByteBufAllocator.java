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
 * 抽象ByteBuf分配器类，作为ByteBufAllocator接口的实现基础。
 * 该类为ByteBuf的分配提供了一种抽象，允许子类实现不同的分配策略。
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    /**
     * 默认的初始化容量
     * 选择256作为默认初始容量是因为它是一个大于128（即2的7次方）的典型值，128是字符集UTF-8中一个字节能表示的最大字符数。
     * 这样可以确保字符串常量池在初始状态下有足够的容量来存储常见的字符串，而不需要立即进行扩容操作。
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;


    /**
     * 构造函数保护级别声明，确保只能在类的内部或子类中实例化。
     * 该构造函数为空，不接受任何参数。
     */
    protected AbstractByteBufAllocator() {
    }


    /**
     * 创建一个默认的堆缓冲区。
     * <p>
     * 该方法旨在提供一个默认的堆缓冲区实例，用于满足常见场景下的数据存储和传输需求。默认情况下，
     * 缓冲区的初始容量为指定的默认值，最大容量为Integer.MAX_VALUE，即理论上允许的最大整数值。
     * 这种配置旨在平衡内存使用和性能需求，适用于大多数情况，而无需手动调整容量。
     *
     * @return 返回一个具有默认初始容量和最大容量的堆缓冲区。
     */
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

    /**
     * 创建一个堆缓冲区。
     * <p>
     * 此方法用于生成一个堆缓冲区实例，堆缓冲区是一种在Java堆内存中分配的缓冲区，适合于当内存效率和访问速度相对较重要时使用。
     * 通过传入初始容量和最大容量参数，可以对缓冲区的大小进行精确控制，以满足特定场景的需求。
     *
     * @param initialCapacity 缓冲区的初始容量。这是分配给缓冲区的初始字节大小。
     * @param maxCapacity     缓冲区的最大容量。这是缓冲区能够增长到的最大字节大小。
     * @return 返回一个新的堆缓冲区实例。
     */
    private ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        // 验证初始容量和最大容量的合法性。
        validate(initialCapacity, maxCapacity);
        // 实际创建并返回堆缓冲区对象。
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * 验证初始容量和最大容量的合法性。
     *
     * 此方法用于在创建容器或数据结构时，验证传入的初始容量和最大容量参数是否满足预定义的条件。
     * 通过抛出IllegalArgumentException来指示调用方传入了非法的参数值。
     *
     * @param initialCapacity 初始化容量，即容器或数据结构开始时的容量。
     * @param maxCapacity 最大容量，即容器或数据结构能容纳的最大元素数量。
     *
     * 参数验证逻辑：
     * 1. 如果初始容量小于等于0，即非正数，则抛出异常，因为容器的初始容量应为正数。
     * 2. 如果初始容量大于最大容量，则抛出异常，因为初始容量不能大于最大容量。
     */
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
     * 创建一个堆内存中的ByteBuf实例。
     * <p>
     * 该方法是抽象的，需要在子类中具体实现。实现时应考虑如何根据指定的初始容量和最大容量创建一个堆内存缓冲区。
     * 初始容量是缓冲区创建时分配的字节数量，而最大容量则是缓冲区能够增长到的最大字节数量。
     * <p>
     * 子类实现时应确保创建的缓冲区既能满足初始和最大容量的限制，又要考虑效率和内存使用优化。
     * <p>
     * 参数:
     * initialCapacity - 指定的初始容量。
     * maxCapacity - 指定的最大容量。
     * <p>
     * 返回:
     * 返回一个堆内存中的ByteBuf实例，该实例的初始容量和最大容量分别不小于initialCapacity和maxCapacity。
     */
    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);


}
