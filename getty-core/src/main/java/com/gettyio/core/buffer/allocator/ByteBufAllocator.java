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

import com.gettyio.core.buffer.bytebuf.ByteBuf;

/**
 * 接口ByteBufAllocator用于分配ByteBuf实例。
 * 它提供了不同的策略来分配内存，例如基于堆的分配或直接在操作系统内存中分配。
 */
public interface ByteBufAllocator {


    /**
     * 分配一个ByteBuf实例。
     * <p>
     * 本方法用于分配一个用于存储字节序列的ByteBuf。具体分配策略由实现决定，
     * 可能返回一个直接缓冲区或堆缓冲区。直接缓冲区的分配可能利用操作系统内存，
     * 而堆缓冲区则使用Java堆内存。调用者应根据实际需求决定是否需要直接缓冲区，
     * 例如，当与本机代码交互或处理大型字节序列时，直接缓冲区可能更有效。
     * <p>
     * 注意：调用者负责ByteBuf的释放，以避免内存泄露。
     *
     * @return 分配的ByteBuf实例。
     */
    ByteBuf buffer();

    /**
     * 创建一个具有指定初始容量的ByteBuf。
     * <p>
     * 此方法用于创建一个ByteBuf实例，该实例的初始容量被指定为参数initialCapacity的值。
     * 初始容量是指在向缓冲区写入数据之前，缓冲区已经预分配的空间大小。
     * <p>
     * 注意：该方法不会限制ByteBuf的最大容量，这意味着随着数据的写入，缓冲区的容量可以动态增长。
     *
     * @param initialCapacity 指定的初始容量。
     * @return 返回一个具有指定初始容量的ByteBuf实例。
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 创建一个具有指定初始容量和最大容量的ByteBuf。
     * <p>
     * 此方法用于创建一个ByteBuf实例，该实例的初始容量和最大容量被分别指定为参数initialCapacity和maxCapacity的值。
     * 初始容量是指在向缓冲区写入数据之前，缓冲区已经预分配的空间大小。最大容量是指缓冲区能够达到的最大容量，超过这个值将无法再向缓冲区写入数据。
     * <p>
     * 注意：如果最大容量小于初始容量，行为将类似于调用了单参数的buffer方法，即忽略最大容量的设定。
     *
     * @param initialCapacity 指定的初始容量。
     * @param maxCapacity     指定的最大容量。
     * @return 返回一个具有指定初始容量和最大容量的ByteBuf实例。
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);


}
