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
 * 实现负责分配缓冲区。这个接口的实现是线程安全的。
 */
public interface ByteBufAllocator {

    /**
     * 分配一个{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer();

    /**
     * 分配一个具有给定初始容量的{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 使用给定的初始容量和最大容量分配一个{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);


}
