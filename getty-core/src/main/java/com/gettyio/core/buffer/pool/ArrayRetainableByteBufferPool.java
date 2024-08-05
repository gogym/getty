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


import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * ArrayRetainableByteBufferPool 类实现了 ByteBuffer 的池化，通过重用 ByteBuffer 对象来减少内存分配和释放的开销。
 * 该类继承自 AbstractByteBufferPool，并且使用数组来管理 ByteBuffer 对象，以提高对象重用的效率。
 */
@SuppressWarnings("resource")
public class ArrayRetainableByteBufferPool extends AbstractByteBufferPool {
    private static final InternalLogger Logger = InternalLoggerFactory.getInstance(ArrayRetainableByteBufferPool.class);


    /**
     * 定义了两个数组，分别用于存储直接和间接保留的Bucket
     */
    private final RetainedBucket[] _directBucket;
    private final RetainedBucket[] _indirect;

    /**
     * 用于跟踪当前使用的堆内内存大小的原子长整型变量
     */
    private final AtomicLong _currentHeapMemory = new AtomicLong();
    /**
     * 用于跟踪当前使用的直接内存大小的原子长整型变量。
     */
    private final AtomicLong _currentDirectMemory = new AtomicLong();

    /**
     * 定义了一个函数，用于根据给定的整数计算Bucket的索引。
     * 这个函数的具体实现可能会根据具体业务逻辑来确定。
     */
    private final Function<Integer, Integer> _bucketIndexFor;


    /**
     * 构造一个具有默认配置的 ArrayRetainableByteBufferPool。
     * {@code maxHeapMemory} 和 {@code maxDirectMemory} 默认为 0，以使用默认的启发式策略。
     */
    public ArrayRetainableByteBufferPool() {
        this(0, -1, -1, Integer.MAX_VALUE, false);
    }

    public ArrayRetainableByteBufferPool(int maxBucketSize, boolean direct) {
        this(0, -1, -1, maxBucketSize, direct);
    }

    /**
     * 构造一个具有给定配置的 ArrayRetainableByteBufferPool。
     * {@code maxHeapMemory} 和 {@code maxDirectMemory} 默认为 0，以使用默认的启发式策略。
     *
     * @param minCapacity   ByteBuffer 的最小容量
     * @param factor        容量因子
     * @param maxCapacity   ByteBuffer 的最大容量
     * @param maxBucketSize 每个桶中 ByteBuffer 的最大数量
     * @param direct        是否使用直接内存
     */
    public ArrayRetainableByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxBucketSize, boolean direct) {
        this(minCapacity, factor, maxCapacity, maxBucketSize, 0L, 0L, direct);
    }

    /**
     * 构造一个具有给定配置的 ArrayRetainableByteBufferPool。
     *
     * @param minCapacity     ByteBuffer 的最小容量
     * @param factor          容量因子
     * @param maxCapacity     ByteBuffer 的最大容量
     * @param maxBucketSize   每个桶中 ByteBuffer 的最大数量
     * @param maxHeapMemory   堆内最大内存大小（字节），-1 表示不限制内存，0 表示使用默认启发式策略
     * @param maxDirectMemory 直接内存的最大大小（字节），-1 表示不限制内存，0 表示使用默认启发式策略
     * @param direct          是否使用直接内存
     */
    public ArrayRetainableByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxBucketSize, long maxHeapMemory, long maxDirectMemory, boolean direct) {
        this(minCapacity, factor, maxCapacity, maxBucketSize, maxHeapMemory, maxDirectMemory, direct, null, null);
    }


    /**
     * 使用给定的配置创建一个新的 ArrayRetainableByteBufferPool。
     *
     * @param minCapacity     ByteBuffer 的最小容量。
     * @param factor          容量因子，用于计算不同容量的 ByteBuffer。
     * @param maxCapacity     ByteBuffer 的最大容量。
     * @param maxBucketSize   每个存储桶中 ByteBuffer 的最大数量。
     * @param maxHeapMemory   堆内存的最大大小（以字节为单位），-1 表示不限制，0 表示使用默认启发式。
     * @param maxDirectMemory 直接内存的最大大小（以字节为单位），-1 表示不限制，0 表示使用默认启发式。
     * @param direct          是否使用直接内存
     * @param bucketIndexFor  一个函数，接受容量并返回存储桶索引。
     * @param bucketCapacity  一个函数，接受存储桶索引并返回容量。
     */
    protected ArrayRetainableByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxBucketSize, long maxHeapMemory, long maxDirectMemory,
                                            boolean direct, Function<Integer, Integer> bucketIndexFor, Function<Integer, Integer> bucketCapacity) {
        // 校验并设置最小容量、容量因子和最大容量。
        if (minCapacity <= 0) {
            minCapacity = 0;
        }
        factor = factor <= 0 ? DEFAULT_FACTOR : factor;
        if (maxCapacity <= 0) {
            maxCapacity = DEFAULT_MAX_CAPACITY_BY_FACTOR * factor;
        }
        // 确保容量因子和最大容量之间存在正确的关系。
        if ((maxCapacity % factor) != 0 || factor >= maxCapacity) {
            throw new IllegalArgumentException(String.format("The capacity factor(%d) must be a divisor of maxCapacity(%d)", factor, maxCapacity));
        }

        // 初始化 bucket 索引和容量计算函数。
        int f = factor;
        if (bucketIndexFor == null) {
            bucketIndexFor = c -> (c - 1) / f;
        }
        if (bucketCapacity == null) {
            bucketCapacity = i -> (i + 1) * f;
        }

        // 根据最大容量计算存储桶数组的长度，并初始化每个存储桶。
        int length = bucketIndexFor.apply(maxCapacity) + 1;
        RetainedBucket[] directArray = new RetainedBucket[length];
        RetainedBucket[] indirectArray = new RetainedBucket[length];
        for (int i = 0; i < directArray.length; i++) {
            // 为每个存储桶计算并设置容量，确保不超过最大容量。
            int capacity = Math.min(bucketCapacity.apply(i), maxCapacity);
            directArray[i] = new RetainedBucket(capacity, maxBucketSize);
            indirectArray[i] = new RetainedBucket(capacity, maxBucketSize);
        }

        // 设置最小容量、最大容量及存储桶相关字段。
        _minCapacity = minCapacity;
        _maxCapacity = maxCapacity;
        _directBucket = directArray;
        _indirect = indirectArray;

        // 设置堆和直接内存的最大大小，根据输入的字节大小计算保留大小。
        _maxHeapMemory = retainedSize(maxHeapMemory);
        _maxDirectMemory = retainedSize(maxDirectMemory);
        _bucketIndexFor = bucketIndexFor;
        _direct = direct;
    }


    @Override
    public RetainableByteBuffer acquire(int size) {
        return acquire(size, _direct);
    }

    /**
     * 获取一个可保留的ByteBuffer对象。
     *
     * @param size   想要获取的ByteBuffer的大小。
     * @param direct 指示是否需要一个直接的ByteBuffer。
     * @return 返回一个符合要求的RetainableByteBuffer对象。
     */
    @Override
    public RetainableByteBuffer acquire(int size, boolean direct) {
        // 根据大小和直接内存标志查找对应的存储桶
        RetainedBucket bucket = bucketFor(size, direct);
        // 如果没有找到合适的存储桶，创建一个新的ByteBuffer
        if (bucket == null) {
            return newRetainableByteBuffer(size, direct, this::removed);
        }

        // 尝试从存储桶中获取一个可用的条目
        RetainedBucket.Entry entry = bucket.acquire();

        RetainableByteBuffer buffer;
        // 如果没有可用的条目，尝试预留一个条目并创建新的ByteBuffer
        if (entry == null) {
            RetainedBucket.Entry reservedEntry = bucket.reserve();
            // 如果成功预留了条目
            if (reservedEntry != null) {
                // 创建新的ByteBuffer，并在释放时对ByteBuffer进行重置，并释放预留的条目
                buffer = newRetainableByteBuffer(size, direct, retainedBuffer ->
                {
                    BufferUtil.reset(retainedBuffer.getBuffer());
                    reservedEntry.release();
                });
                reservedEntry.enable(buffer, true);
                // 更新当前内存使用情况，并尝试释放超出的内存
                if (direct) {
                    _currentDirectMemory.addAndGet(buffer.capacity());
                } else {
                    _currentHeapMemory.addAndGet(buffer.capacity());
                }
                releaseExcessMemory(direct);
            } else {
                // 如果无法预留条目，则直接创建新的ByteBuffer
                buffer = newRetainableByteBuffer(size, direct, this::removed);
            }
        } else {
            // 如果有可用的条目，获取关联的ByteBuffer并增加其使用计数
            buffer = entry.getPooled();
            buffer.acquire();
        }
        return buffer;
    }

    /**
     * 分配一个ByteBuffer对象，容量为指定大小。
     *
     * @param capacity 指定的ByteBuffer容量。
     * @return 分配的ByteBuffer对象。
     */
    private ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    /**
     * 分配一个直接分配内存的ByteBuffer对象，容量为指定大小。
     *
     * @param capacity 指定的ByteBuffer容量。
     * @return 分配的直接内存ByteBuffer对象。
     */
    private ByteBuffer allocateDirect(int capacity) {
        return ByteBuffer.allocateDirect(capacity);
    }

    /**
     * 当RetainableByteBuffer被释放时执行的回调方法。
     *
     * @param retainedBuffer 被释放的RetainableByteBuffer对象。
     */
    private void removed(RetainableByteBuffer retainedBuffer) {
    }

    /**
     * 创建一个新的RetainableByteBuffer对象。
     *
     * @param capacity ByteBuffer的容量。
     * @param direct   指示是否创建直接内存的ByteBuffer。
     * @param releaser 当这个ByteBuffer不再需要时的释放回调。
     * @return 创建的RetainableByteBuffer对象。
     */
    private RetainableByteBuffer newRetainableByteBuffer(int capacity, boolean direct, Consumer<RetainableByteBuffer> releaser) {
        // 根据direct标志分配直接或堆内存
        ByteBuffer buffer = direct ? allocateDirect(capacity) : allocate(capacity);
        // 清空缓冲区
        BufferUtil.clear(buffer);
        // 创建可保留的ByteBuffer
        RetainableByteBuffer retainableByteBuffer = new RetainableByteBuffer(buffer, releaser);
        // 增加保留计数
        retainableByteBuffer.acquire();
        return retainableByteBuffer;
    }

    /**
     * 根据容量和是否直接内存标志查找对应的存储桶。
     *
     * @param capacity 缓冲区容量。
     * @param direct   指示是否需要直接内存。
     * @return 对应的存储桶，如果没有找到则返回null。
     */
    private RetainedBucket bucketFor(int capacity, boolean direct) {
        // 容量小于最小容量时，直接返回null
        if (capacity < _minCapacity) {
            return null;
        }
        // 计算存储桶索引
        int idx = _bucketIndexFor.apply(capacity);
        // 根据是否直接内存选择存储桶数组
        RetainedBucket[] buckets = direct ? _directBucket : _indirect;
        // 索引超出范围，返回null
        if (idx >= buckets.length) {
            return null;
        }
        // 返回对应的存储桶
        return buckets[idx];
    }

    /**
     * 根据指定类型获取保留的内存大小
     *
     * @param direct 指示是否获取直接ByteBuffers保留的内存，为true时获取直接内存，为false时获取堆内存
     * @return 指定类型ByteBuffers保留的内存大小
     */
    private long getMemory(boolean direct) {
        // 根据参数决定返回直接内存还是堆内存
        if (direct) {
            // 获取直接内存
            return _currentDirectMemory.get();
        } else {
            // 获取堆内存
            return _currentHeapMemory.get();
        }
    }

    /**
     * 清空当前的RetainableByteBufferPool
     * 该方法会清空直接和间接内存池中的所有条目，并更新内存计数器
     */
    public void clear() {
        // 分别清空直接内存池和堆内存池
        clearArray(_directBucket, _currentDirectMemory);
        clearArray(_indirect, _currentHeapMemory);
    }

    /**
     * 清空指定内存池数组，并更新相应的内存计数器
     *
     * @param poolArray     内存池数组，包含多个保留桶
     * @param memoryCounter 内存计数器，用于追踪内存使用情况
     */
    private void clearArray(RetainedBucket[] poolArray, AtomicLong memoryCounter) {
        // 遍历内存池数组中的每个保留桶
        for (RetainedBucket pool : poolArray) {
            // 遍历保留桶中的每个条目
            for (RetainedBucket.Entry entry : pool.idleValues()) {
                // 尝试移除条目，成功则更新内存计数器
                if (entry.remove()) {
                    memoryCounter.addAndGet(-entry.getPooled().capacity()); // 减去移除条目所占的内存容量
                    removed(entry.getPooled()); // 处理条目移除的逻辑
                }
            }

            for (RetainedBucket.Entry entry : pool.inUseValues()) {
                // 尝试移除条目，成功则更新内存计数器
                if (entry.remove()) {
                    memoryCounter.addAndGet(-entry.getPooled().capacity()); // 减去移除条目所占的内存容量
                    removed(entry.getPooled()); // 处理条目移除的逻辑
                }
            }

        }
    }

    /**
     * 释放超出最大内存限制的内存。
     * 该方法会检查当前使用的内存是否超出了设定的最大直接内存或最大堆内存限制。
     * 如果超出，则会尝试驱逐指定类型的内存，以确保内存使用在允许的范围内。
     *
     * @param direct 指示是否释放直接内存。为true时释放直接内存，为false时释放堆内存。
     */
    private void releaseExcessMemory(boolean direct) {
        // 根据是否释放直接内存，选择对应的最大内存限制
        long maxMemory = direct ? _maxDirectMemory : _maxHeapMemory;
        // 当最大内存限制大于0时，进行内存检查和释放
        if (maxMemory > 0) {
            // 计算当前内存使用量与最大内存限制的差值
            long excess = getMemory(direct) - maxMemory;
            // 当超出的内存大于0时，触发驱逐操作
            if (excess > 0) {
                evict(direct, excess);
            }
        }
    }

    /**
     * 使用淘汰机制寻找最早释放的RetainableByteBuffers。
     *
     * @param direct 指定是否在直接缓冲区桶中搜索。为true时在直接缓冲区桶中搜索，为false时在堆缓冲区桶中搜索。
     * @param excess 需要淘汰的字节量。至少从缓冲区桶中移除这么多字节。
     */
    private void evict(boolean direct, long excess) {
        // 如果日志级别为DEBUG，则打印开始淘汰的信息
        if (Logger.isDebugEnabled()) {
            Logger.debug("evicting {} bytes from {} pools", excess, (direct ? "direct" : "heap"));
        }
        long now = System.nanoTime();
        long totalClearedCapacity = 0L; // 记录已清除的总容量

        RetainedBucket[] buckets = direct ? _directBucket : _indirect; // 根据direct选择对应的缓冲区桶数组

        //尝试释放缓冲区数量
        int count = 0;

        // 循环，直到清除的容量大于等于excess
        while (totalClearedCapacity < excess) {
            //防止无限循环，检查已尝试释放的缓冲区数量是否超过最大容量
            if (count >= getMaxCapacity()) {
                throw new IllegalStateException("The maximum memory limit is exceeded");
            }
            // 遍历桶中的条目，寻找最旧的条目进行淘汰
            for (RetainedBucket bucket : buckets) {
                count++;
                // 寻找空闲最旧的条目
                RetainedBucket.Entry oldestEntry = findOldestIdleEntry(now, bucket);
                if (oldestEntry == null) {
                    // 如果没有找到条目，则继续下一个桶
                    continue;
                }

                // 尝试移除最旧的条目
                if (oldestEntry.remove()) {
                    // 获取清除的容量
                    int clearedCapacity = oldestEntry.getPooled().capacity();
                    // 根据缓冲区类型，更新当前内存使用量
                    if (direct) {
                        _currentDirectMemory.addAndGet(-clearedCapacity);
                    } else {
                        _currentHeapMemory.addAndGet(-clearedCapacity);
                    }
                    // 更新已清除的总容量
                    totalClearedCapacity += clearedCapacity;
                    // 执行移除操作的后续处理
                    removed(oldestEntry.getPooled());
                }
                // 如果同时有其他线程尝试移除相同的条目，则不计算其容量
            }
        }

        // 如果日志级别为DEBUG，则打印淘汰完成的信息
        if (Logger.isDebugEnabled()) {
            Logger.debug("eviction done, cleared {} bytes from {} pools", totalClearedCapacity, (direct ? "direct" : "heap"));
        }
    }

    /**
     * 在指定的缓冲区桶中寻找最旧的空闲条目。
     *
     * @param now    当前时间，用于计算条目的年龄。
     * @param bucket 缓冲区桶，存储着待搜索的缓冲区条目。
     * @return Pool<RetainableByteBuffer>.Entry 最旧的条目，如果没有条目则返回null。
     */
    private Pool<RetainableByteBuffer>.Entry findOldestIdleEntry(long now, Pool<RetainableByteBuffer> bucket) {
        // 初始化最旧条目为null
        RetainedBucket.Entry oldestEntry = null;
        // 遍历桶中的所有条目
        for (RetainedBucket.Entry entry : bucket.idleValues()) {
            // 如果已经找到过条目
            if (oldestEntry != null) {
                // 计算当前条目的年龄
                long entryAge = now - entry.getPooled().getLastUpdate();
                // 如果当前条目比之前找到的最旧条目更旧，则更新最旧条目
                if (entryAge > now - oldestEntry.getPooled().getLastUpdate()) {
                    oldestEntry = entry;
                }
            } else {
                // 如果还没有找到条目，直接将当前条目设置为最旧条目
                oldestEntry = entry;
            }
        }
        // 返回最旧的条目
        return oldestEntry;
    }

    @Override
    public String toString() {
        return String.format("%s{min=%d,max=%d,buckets=%d,heap=%d/%d,direct=%d/%d}",
                super.toString(),
                _minCapacity, _maxCapacity,
                _directBucket.length,
                _currentHeapMemory.get(), _maxHeapMemory,
                _currentDirectMemory.get(), _maxDirectMemory);
    }


    /**
     * 一个特定于保留ByteBuffer对象的池类，继承自Pool类。
     * 该池限制了它可以保留的ByteBuffer对象的数量，并且可以跟踪哪些对象当前正在被使用。
     */
    private static class RetainedBucket extends Pool<RetainableByteBuffer> {

        /**
         * 池容量，即最多能保留的ByteBuffer对象数量
         */
        private final int _capacity;

        /**
         * RetainedBucket构造函数。
         *
         * @param capacity 池的容量。
         * @param size     池中对象的最大数量。
         */
        RetainedBucket(int capacity, int size) {
            // 使用线程ID作为键策略，允许对象被多个线程共享。
            super(size, true);
            // 初始化容量。
            _capacity = capacity;
        }

        /**
         * 重写toString方法，以提供关于池状态的字符串表示，包括容量、当前使用中的对象数量及其百分比。
         *
         * @return 描述池状态的字符串。
         */
        @Override
        public String toString() {
            // 记录池中对象的数量。
            int entries = size();
            // 记录当前正在使用中的对象数量。
            int inUse = getInUseCount();

            // 格式化并返回池状态的字符串表示。
            return String.format("%s{capacity=%d,inuse=%d(%d%%)}",
                    super.toString(),
                    _capacity,
                    inUse,
                    entries > 0 ? (inUse * 100) / entries : 0);
        }
    }
}
