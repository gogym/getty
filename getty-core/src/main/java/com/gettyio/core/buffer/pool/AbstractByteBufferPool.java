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
 * 抽象类 `AbstractByteBufferPool` 实现了 `ByteBufferPool` 接口，提供了一个缓冲区池的基本实现。
 * 该类定义了缓冲区池的最小和最大容量，以及最大堆内存和直接内存的限制。
 * 默认情况下，`maxHeapMemory` 和 `maxDirectMemory` 的启发式设置是使用 {@link Runtime#maxMemory()} 除以 4。
 */
abstract class AbstractByteBufferPool implements ByteBufferPool {

    /**
     * 默认的因子值，用于计算最大容量。
     * 默认因子值为4096，表示最大容量可以通过这个因子乘以当前容量来估算。
     */
    public final int DEFAULT_FACTOR = 4096;

    /**
     * 默认按因子计算的最大容量值。
     * 当缓冲区的容量超过这个值时，将会触发特定的容量控制逻辑。
     * 默认最大容量为16，通过DEFAULT_FACTOR乘以当前容量来估算。
     */
    public final int DEFAULT_MAX_CAPACITY_BY_FACTOR = 16;

    /**
     * 最小池化缓冲区容量。
     * 该变量用于控制缓冲区的最小尺寸，确保缓冲区不会过小而无法有效地处理数据。
     */
    public int _minCapacity;

    /**
     * 最大池化缓冲区容量。
     * 该变量用于控制缓冲区的最大尺寸，防止缓冲区过大而消耗过多的内存资源。
     */
    public int _maxCapacity;

    /**
     * 最大堆内存限制。
     * 该变量用于限制缓冲区在堆内存中使用的最大空间，防止缓冲区过度增长导致内存溢出。
     */
    public long _maxHeapMemory;

    /**
     * 最大直接内存限制。
     * 该变量用于限制缓冲区在直接内存中使用的最大空间，直接内存通常用于提高数据传输速率。
     */
    public long _maxDirectMemory;


    /**
     * 是否使用直接内存
     * 默认 false
     */
    public boolean _direct = false;


    /**
     * 获取最小池化缓冲区容量。
     *
     * @return 返回设置的最小缓冲区容量。
     */
    public int getMinCapacity() {
        return _minCapacity;
    }

    /**
     * 获取最大池化缓冲区容量。
     *
     * @return 返回设置的最大缓冲区容量。
     */
    public int getMaxCapacity() {
        return _maxCapacity;
    }

    /**
     * 计算保留大小。
     * 如果大小不为-2，将调用memorySize方法进行内存大小计算。
     *
     * @param size 缓冲区的大小。
     * @return 计算后的保留大小。
     */
    protected long retainedSize(long size) {
        if (size != -2) {
            return memorySize(size);
        }
        return 0;
    }

    /**
     * 计算内存大小。
     * 如果大小大于等于0且不为0，返回该大小；否则，返回通过Runtime.maxMemory()方法计算得到的最大内存的四分之一。
     *
     * @param size 缓冲区的大小。
     * @return 计算后的内存大小。
     */
    protected long memorySize(long size) {
        if (size >= 0) {
            if (size != 0) {
                return size;
            }
            return Runtime.getRuntime().maxMemory() / 4;
        } else {
            return -1;
        }
    }


    /**
     * 获取是否为直接模式
     *
     * @return true表示是直接模式，false表示不是直接模式
     */
    public boolean isDirect() {
        return _direct;
    }

    /**
     * 设置是否为直接模式
     *
     * @param direct true设置为直接模式，false设置为非直接模式
     */
    public void setDirect(boolean direct) {
        this._direct = direct;
    }
}

