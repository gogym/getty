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


import com.gettyio.core.buffer.allocator.AbstractByteBufAllocator;
import com.gettyio.core.buffer.bytebuf.ByteBuf;
import com.gettyio.core.buffer.bytebuf.impl.UnpooledHeapByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 池化缓存区构造器
 */
public class PooledByteBufAllocator extends AbstractByteBufAllocator {
    private final static Logger logger = LoggerFactory.getLogger(PooledByteBufAllocator.class);


    // 默认的堆区域数量
    private static final int DEFAULT_NUM_HEAP_ARENA;

    // 默认的页面大小
    private static final int DEFAULT_PAGE_SIZE;

    // 默认的最大订单，决定最大块的大小
    private static final int DEFAULT_MAX_ORDER; // 8192 << 11 = 16 MiB per chunk

    // 默认的小缓存大小
    private static final int DEFAULT_TINY_CACHE_SIZE;
    // 默认的小缓存大小
    private static final int DEFAULT_SMALL_CACHE_SIZE;
    // 默认的正常缓存大小
    private static final int DEFAULT_NORMAL_CACHE_SIZE;
    // 默认的最大缓存容量
    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY;
    // 默认的缓存修剪间隔
    private static final int DEFAULT_CACHE_TRIM_INTERVAL;

    // 最小页面大小，确保页面大小至少为4096字节
    private static final int MIN_PAGE_SIZE = 4096;

    // 最大块大小，防止创建过大的块，最大的单chunk的大小为1G
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);


    static {
        // 设置内存页的大小
        int defaultPageSize = 8192;

        Throwable pageSizeFallbackCause = null;
        try {
            //求以2为底的8192的对数,即13
            validateAndCalculatePageShifts(defaultPageSize);
        } catch (Throwable t) {
            pageSizeFallbackCause = t;
            defaultPageSize = 8192;
        }
        DEFAULT_PAGE_SIZE = defaultPageSize;

        //最大的Order
        int defaultMaxOrder = 11;
        Throwable maxOrderFallbackCause = null;
        try {
            //确认当前的chunkSize不会超过最大chunkSize(1G)的一半（512M）
            validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder);
        } catch (Throwable t) {
            maxOrderFallbackCause = t;
            defaultMaxOrder = 11;
        }
        DEFAULT_MAX_ORDER = defaultMaxOrder;

        // Determine reasonable default for nHeapArena and nDirectArena.
        //确保合理的nheapArena 和 ndirectArena
        // Assuming each arena has 3 chunks, the pool should not consume more
        // than 50% of max memory.
        //假设有3块chunk ,      这个内存池不能超过最大内存的一半  
        final Runtime runtime = Runtime.getRuntime();
        final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER;
        //设置默认的堆内存
        DEFAULT_NUM_HEAP_ARENA = Math.max(0, (int) Math.min(runtime.availableProcessors(), Runtime.getRuntime().maxMemory() / defaultChunkSize / 2 / 3));
        //最小块cahce的大小
        DEFAULT_TINY_CACHE_SIZE = 512;
        //较小的cache的大小
        DEFAULT_SMALL_CACHE_SIZE = 256;
        //正常的cache的大小
        DEFAULT_NORMAL_CACHE_SIZE = 64;

        // 默认最大缓存数组长度为32kb。类似于“使用jemalloc进行可伸缩内存分配”
        DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024;

        // 如果不经常使用，缓存条目将被释放时的分配阈值数量
        DEFAULT_CACHE_TRIM_INTERVAL = 8192;

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.allocator.numHeapArenas: {}", DEFAULT_NUM_HEAP_ARENA);
            if (pageSizeFallbackCause == null) {
                logger.debug("-Dio.allocator.pageSize: {}", DEFAULT_PAGE_SIZE);
            } else {
                logger.debug("-Dio.allocator.pageSize: {}", DEFAULT_PAGE_SIZE, pageSizeFallbackCause);
            }
            if (maxOrderFallbackCause == null) {
                logger.debug("-Dio.allocator.maxOrder: {}", DEFAULT_MAX_ORDER);
            } else {
                logger.debug("-Dio.allocator.maxOrder: {}", DEFAULT_MAX_ORDER, maxOrderFallbackCause);
            }
            logger.debug("-Dio.allocator.chunkSize: {}", DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER);
            logger.debug("-Dio.allocator.tinyCacheSize: {}", DEFAULT_TINY_CACHE_SIZE);
            logger.debug("-Dio.allocator.smallCacheSize: {}", DEFAULT_SMALL_CACHE_SIZE);
            logger.debug("-Dio.allocator.normalCacheSize: {}", DEFAULT_NORMAL_CACHE_SIZE);
            logger.debug("-Dio.allocator.maxCachedBufferCapacity: {}", DEFAULT_MAX_CACHED_BUFFER_CAPACITY);
            logger.debug("-Dio.allocator.cacheTrimInterval: {}", DEFAULT_CACHE_TRIM_INTERVAL);
        }
    }

    /**
     * 堆内存池数组，用于存储不同大小的内存池。
     * 这些内存池分别用于分配小、中、大型的 ByteBuf。
     */
    private final PoolArena<byte[]>[] heapArenas;

    /**
     * 小对象缓存区的大小设置。
     * 这里定义了小对象缓存区能够存储的对象数量。
     * 小对象是指长度小于等于 tinyCacheSize 的 ByteBuf。
     */
    private final int tinyCacheSize;

    /**
     * 中等对象缓存区的大小设置。
     * 这里定义了中等对象缓存区能够存储的对象数量。
     * 中等对象是指长度小于 smallCacheSize 的 ByteBuf。
     */
    private final int smallCacheSize;

    /**
     * 标准对象缓存区的大小设置。
     * 这里定义了标准对象缓存区能够存储的对象数量。
     * 标准对象是指长度小于 normalCacheSize 的 ByteBuf。
     */
    private final int normalCacheSize;

    /**
     * ThreadLocal缓存实例，用于每个线程私有的缓存数据。
     * 这样设计可以减少跨线程的数据共享和同步开销，提高缓存的访问效率。
     */
    final PoolThreadLocalCache threadCache;


    public PooledByteBufAllocator() {
        this(DEFAULT_NUM_HEAP_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }


    public PooledByteBufAllocator(int nHeapArena, int pageSize, int maxOrder) {
        this(nHeapArena, pageSize, maxOrder, DEFAULT_TINY_CACHE_SIZE,
                DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE);
    }

    /**
     * 构造函数用于创建一个PooledByteBufAllocator。
     *
     * @param nHeapArena 分配的堆内存区域数量。堆内存区域用于管理Java堆上的内存分配。
     * @param pageSize 每个内存页的大小。内存页是分配内存的基本单位。
     * @param maxOrder 内存页大小的最大对数。这影响了可以分配的最大块大小。
     * @param tinyCacheSize 小块缓存的大小。小块是指小于某个阈值的内存块。
     * @param smallCacheSize 中等大小块缓存的大小。中等大小块是指介于小块和正常大小块之间的内存块。
     * @param normalCacheSize 正常大小块缓存的大小。正常大小块是指大于中等大小块的内存块。
     */
    public PooledByteBufAllocator(int nHeapArena, int pageSize, int maxOrder,
                                      int tinyCacheSize, int smallCacheSize, int normalCacheSize) {
        super();
        // 初始化线程本地缓存，用于存储每个线程的缓存块，提高缓存效率。
        threadCache = new PoolThreadLocalCache();
        // 设置小、中、正常缓存的大小，用于管理不同大小的内存块。
        this.tinyCacheSize = tinyCacheSize;
        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;
        // 校验并计算内存块的大小，这是分配内存的基本单元。
        final int chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        // 校验nHeapArena参数是否合法，不允许负数。
        if (nHeapArena < 0) {
            throw new IllegalArgumentException("nHeapArena: " + nHeapArena + " (expected: >= 0)");
        }

        // 校验并计算内存页的位移，用于计算内存分配。
        int pageShifts = validateAndCalculatePageShifts(pageSize);

        // 根据nHeapArena的值决定是否创建堆内存区域。
        if (nHeapArena > 0) {
            // 初始化堆内存区域数组。
            heapArenas = newArenaArray(nHeapArena);
            // 为每个堆内存区域分配资源。
            for (int i = 0; i < heapArenas.length; i++) {
                heapArenas[i] = new PoolArena.HeapArena(this, pageSize, maxOrder, pageShifts, chunkSize);
            }
        } else {
            // 如果不需要堆内存区域，则设置为null。
            heapArenas = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> PoolArena<T>[] newArenaArray(int size) {
        return new PoolArena[size];
    }

    /**
     * 对内存页进行验证是否在正确的范围内
     *
     * @param pageSize 内存页的大小
     * @return 结果
     */
    private static int validateAndCalculatePageShifts(int pageSize) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: " + MIN_PAGE_SIZE + "+)");
        }

        if ((pageSize & pageSize - 1) != 0) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2)");
        }

        // Logarithm base 2. At this point we know that pageSize is a power of
        // two.
        //求以为2底的对数  log2(8192),简单计算办法
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(pageSize);
    }

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        //进行最大数的验证
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        //确保这个chunksize不溢出
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i--) {
            //确保单chunk的大小不超过512M
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format("pageSize (%d) << maxOrder (%d) must not exceed %d",
                        pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    /**
     * 创建一个新的堆内存缓冲区。
     *
     * 当需要在堆内存中分配一个新的ByteBuf时，这个方法会被调用。它首先尝试从线程缓存中获取一个池化区域（heapArena）。
     * 如果获取成功，那么它会尝试在这个池化区域中分配内存；如果获取失败（即heapArena为null），那么它会退回到使用
     * UnpooledHeapByteBuf的分配方式。这种方式不利用池化技术，而是直接在堆内存中分配一块连续的空间。
     *
     * @param initialCapacity 新缓冲区的初始容量。这是实际分配的内存大小。
     * @param maxCapacity 新缓冲区的最大容量。这是缓冲区允许增长到的最大值。
     * @return 返回一个新的堆内存缓冲区实例。
     */
    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        // 尝试从线程缓存中获取PoolThreadCache实例。
        PoolThreadCache cache = threadCache.get();
        // 从线程缓存中获取heapArena，用于后续的堆内存分配。
        PoolArena<byte[]> heapArena = cache.heapArena;

        ByteBuf buf;
        // 如果heapArena不为空，尝试使用池化的heapArena进行内存分配。
        if (heapArena != null) {
            buf = heapArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            // 如果heapArena为空，则退回到非池化的内存分配方式。
            buf = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
        }

        return buf;
    }


    /**
     * 自定义ThreadLocal类，用于缓存PoolThreadCache对象。
     * 该类的作用是在每个线程中维护一个独立的缓存空间，用于存放池化的缓存对象，以提高缓存的效率和线程安全性。
     */
    final class PoolThreadLocalCache extends ThreadLocal<PoolThreadCache> {
        // 用于线程安全的索引自增，确保每个线程获取到的缓存对象是独立的。
        private final AtomicInteger index = new AtomicInteger();

        /**
         * 重写ThreadLocal的initialValue方法，用于初始化每个线程的缓存对象。
         * 这里创建了一个PoolThreadCache实例，它可能与一个heapArena相关联，heapArena是根据线程索引计算得到的。
         *
         * @return 返回一个新的PoolThreadCache实例，该实例根据线程索引和预设的缓存大小参数初始化。
         */
        @Override
        protected PoolThreadCache initialValue() {
            // 获取当前线程的索引，并自增以保证每个线程的索引唯一。
            final int idx = index.getAndIncrement();
            // 根据索引计算并获取对应的heapArena
            final PoolArena<byte[]> heapArena;

            if (heapArenas != null) {
                // 使用取模运算确保索引不会超出heapArenas的长度，并保证缓存的均匀分配。
                heapArena = heapArenas[Math.abs(idx % heapArenas.length)];
            } else {
                heapArena = null;
            }

            // 创建并返回一个新的PoolThreadCache实例，它可能与一个heapArena相关联，并根据预设的缓存大小参数初始化。
            return new PoolThreadCache(heapArena, tinyCacheSize, smallCacheSize, normalCacheSize,
                    DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);
        }
    }

}
