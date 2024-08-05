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


/**
 * ByteBufferPool接口定义了一个ByteBuffer的池化机制。
 * 通过这个接口，可以获取到一定大小的ByteBuffer实例，用于减少频繁创建和销毁ByteBuffer对象带来的性能开销。
 */
public interface ByteBufferPool {

    /**
     * 从池中获取一个ByteBuffer实例。
     *
     * @param size 需要获取的ByteBuffer的大小。这个大小是指ByteBuffer能够存储的数据的字节长度。
     * @param direct 指定是否需要一个直接的ByteBuffer。直接的ByteBuffer会在Java堆外分配内存，可能会提高某些情况下的性能。
     * @return 返回一个RetainableByteBuffer实例，这个实例可以从池中获取并使用，用完后需要释放以返回池中。
     */
    RetainableByteBuffer acquire(int size, boolean direct);

    /**
     * 申请一个缓冲区
     *
     * @param size
     * @return
     */
    RetainableByteBuffer acquire(int size);


}

