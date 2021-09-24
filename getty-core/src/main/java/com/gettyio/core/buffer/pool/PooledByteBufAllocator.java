/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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
import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.buffer.pool.buffer.UnpooledDirectByteBuf;
import com.gettyio.core.buffer.pool.buffer.UnpooledHeapByteBuf;
import com.gettyio.core.buffer.pool.buffer.UnpooledUnsafeDirectByteBuf;
import com.gettyio.core.util.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 池化缓存区构造器
 */
public class PooledByteBufAllocator extends AbstractByteBufAllocator {
    private final static Logger logger = LoggerFactory.getLogger(PooledByteBufAllocator.class);

    /**
     * 堆内存
     */
    private static final int DEFAULT_NUM_HEAP_ARENA;

    /**
     * 直接内存
     */
    private static final int DEFAULT_NUM_DIRECT_ARENA;

    /**
     * 单内存页的大小
     */
    private static final int DEFAULT_PAGE_SIZE;

    /**
     * 最大的order，默认为11
     */
    private static final int DEFAULT_MAX_ORDER; // 8192 << 11 = 16 MiB per chunk


    private static final int DEFAULT_TINY_CACHE_SIZE;
    private static final int DEFAULT_SMALL_CACHE_SIZE;
    private static final int DEFAULT_NORMAL_CACHE_SIZE;
    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY;
    private static final int DEFAULT_CACHE_TRIM_INTERVAL;

    private static final int MIN_PAGE_SIZE = 4096;

    /**
     * 最大的单chunk的大小为1G
     */
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
        int defaultMaxOrder =  11;
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
        DEFAULT_NUM_HEAP_ARENA = Math.max(0,  (int) Math.min(runtime.availableProcessors(), Runtime.getRuntime().maxMemory() / defaultChunkSize / 2 / 3));
        //直接的内存
        DEFAULT_NUM_DIRECT_ARENA = Math.max(0, (int) Math.min(runtime.availableProcessors(), PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3));

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
            logger.debug("-Dio.allocator.numDirectArenas: {}", DEFAULT_NUM_DIRECT_ARENA);
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

    private final PoolArena<byte[]>[] heapArenas;
    private final PoolArena<ByteBuffer>[] directArenas;
    private final int tinyCacheSize;
    private final int smallCacheSize;
    private final int normalCacheSize;

    final PoolThreadLocalCache threadCache;

    public PooledByteBufAllocator() {
        this(false);
    }

    public PooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    public PooledByteBufAllocator(int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(false, nHeapArena, nDirectArena, pageSize, maxOrder);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder, DEFAULT_TINY_CACHE_SIZE,
                DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder,
                                  int tinyCacheSize, int smallCacheSize, int normalCacheSize) {
        super(preferDirect);
        threadCache = new PoolThreadLocalCache();
        this.tinyCacheSize = tinyCacheSize;
        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;
        final int chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        if (nHeapArena < 0) {
            throw new IllegalArgumentException("nHeapArena: " + nHeapArena + " (expected: >= 0)");
        }
        if (nDirectArena < 0) {
            throw new IllegalArgumentException("nDirectArea: " + nDirectArena + " (expected: >= 0)");
        }

        int pageShifts = validateAndCalculatePageShifts(pageSize);

        if (nHeapArena > 0) {
            heapArenas = newArenaArray(nHeapArena);
            for (int i = 0; i < heapArenas.length; i++) {
                heapArenas[i] = new PoolArena.HeapArena(this, pageSize, maxOrder, pageShifts, chunkSize);
            }
        } else {
            heapArenas = null;
        }

        if (nDirectArena > 0) {
            directArenas = newArenaArray(nDirectArena);
            for (int i = 0; i < directArenas.length; i++) {
                directArenas[i] = new PoolArena.DirectArena(this, pageSize, maxOrder, pageShifts, chunkSize);
            }
        } else {
            directArenas = null;
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

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        PoolArena<byte[]> heapArena = cache.heapArena;

        ByteBuf buf;
        if (heapArena != null) {
            buf = heapArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            buf = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
        }

        return buf;
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        PoolArena<ByteBuffer> directArena = cache.directArena;

        ByteBuf buf;
        if (directArena != null) {
            buf = directArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            if (PlatformDependent.hasUnsafe()) {
                buf = new UnpooledUnsafeDirectByteBuf(this, initialCapacity, maxCapacity);
            } else {
                buf = new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
            }
        }

        return buf;
    }

    @Override
    public boolean isDirectBufferPooled() {
        return directArenas != null;
    }

    final class PoolThreadLocalCache extends ThreadLocal<PoolThreadCache> {
        private final AtomicInteger index = new AtomicInteger();

        @Override
        protected PoolThreadCache initialValue() {
            final int idx = index.getAndIncrement();
            final PoolArena<byte[]> heapArena;
            final PoolArena<ByteBuffer> directArena;

            if (heapArenas != null) {
                heapArena = heapArenas[Math.abs(idx % heapArenas.length)];
            } else {
                heapArena = null;
            }

            if (directArenas != null) {
                directArena = directArenas[Math.abs(idx % directArenas.length)];
            } else {
                directArena = null;
            }

            return new PoolThreadCache(heapArena, directArena, tinyCacheSize, smallCacheSize, normalCacheSize,
                    DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);
        }
    }

}
