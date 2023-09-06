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


import com.gettyio.core.buffer.bytebuf.impl.PooledByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为分配充当线程缓存。这个实现将在之后模块化
 */
final class PoolThreadCache {
    private final static Logger logger = LoggerFactory.getLogger(PoolThreadCache.class);
    final PoolArena<byte[]> heapArena;

    // 为不同大小的类保留缓存，这些类很小、小和正常。
    private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;

    // 用于以后计算普通缓存索引时的位移
    private final int numShiftsNormalHeap;
    private final int freeSweepAllocationThreshold;

    private int allocations;

    private final Thread thread = Thread.currentThread();
    private final Runnable freeTask = new Runnable() {
        @Override
        public void run() {
            free0();
        }
    };

    PoolThreadCache(PoolArena<byte[]> heapArena,
                    int tinyCacheSize, int smallCacheSize, int normalCacheSize,
                    int maxCachedBufferCapacity, int freeSweepAllocationThreshold) {
        if (maxCachedBufferCapacity < 0) {
            throw new IllegalArgumentException("maxCachedBufferCapacity: "
                    + maxCachedBufferCapacity + " (expected: >= 0)");
        }
        if (freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                    + maxCachedBufferCapacity + " (expected: > 0)");
        }
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        this.heapArena = heapArena;

        if (heapArena != null) {
            // 为堆分配创建缓存
            tinySubPageHeapCaches = createSubPageCaches(tinyCacheSize, PoolArena.numTinySubpagePools);
            smallSubPageHeapCaches = createSubPageCaches(smallCacheSize, heapArena.numSmallSubpagePools);

            numShiftsNormalHeap = log2(heapArena.pageSize);
            normalHeapCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, heapArena);
        } else {
            // 没有配置heapArea，所以清空所有缓存
            tinySubPageHeapCaches = null;
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
            numShiftsNormalHeap = -1;
        }

        // 线程本地缓存将保存一个缓冲池列表，当线程不再活跃时，这些缓冲池必须返回到池中。
        ThreadDeathWatcher.watch(thread, freeTask);
    }

    private static <T> SubPageMemoryRegionCache<T>[] createSubPageCaches(int cacheSize, int numCaches) {
        if (cacheSize > 0) {
            @SuppressWarnings("unchecked")
            SubPageMemoryRegionCache<T>[] cache = new SubPageMemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                // TODO: maybe use cacheSize / cache.length
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static <T> NormalMemoryRegionCache<T>[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            int arraySize = Math.max(1, max / area.pageSize);

            @SuppressWarnings("unchecked")
            NormalMemoryRegionCache<T>[] cache = new NormalMemoryRegionCache[arraySize];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new NormalMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static int log2(int val) {
        int res = 0;
        while (val > 1) {
            val >>= 1;
            res++;
        }
        return res;
    }

    /**
     * 尝试从缓存中分配一个小缓冲区。 Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateTiny(PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForTiny(normCapacity), buf, reqCapacity);
    }

    /**
     * 尝试从缓存中分配一个小缓冲区。 Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateSmall(PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForSmall(normCapacity), buf, reqCapacity);
    }

    /**
     * 尝试从缓存中分配一个小缓冲区。 Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateNormal(PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForNormal(normCapacity), buf, reqCapacity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        boolean allocated = cache.allocate(buf, reqCapacity);
        if (++allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }

    /**
     * 如果有足够的空间，添加 {@link PoolChunk} and {@code handle} 到缓冲
     * Returns {@code true} if it fit into the cache {@code false} otherwise.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean add(PoolArena<?> area, PoolChunk chunk, long handle, int normCapacity) {
        MemoryRegionCache<?> cache;
        if (area.isTinyOrSmall(normCapacity)) {
            if (PoolArena.isTiny(normCapacity)) {
                cache = cacheForTiny(normCapacity);
            } else {
                cache = cacheForSmall(normCapacity);
            }
        } else {
            cache = cacheForNormal(normCapacity);
        }
        if (cache == null) {
            return false;
        }
        return cache.add(chunk, handle);
    }

    /**
     * 如果使用此缓存的线程即将退出，从缓存中释放资源
     */
    void free() {
        ThreadDeathWatcher.unwatch(thread, freeTask);
        free0();
    }

    private void free0() {
        int numFreed =
                free(tinySubPageHeapCaches) +
                        free(smallSubPageHeapCaches) +
                        free(normalHeapCaches);

        if (numFreed > 0 && logger.isDebugEnabled()) {
            logger.debug("Freed {} thread-local buffer(s) from thread: {}", numFreed, thread.getName());
        }
    }

    private static int free(MemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MemoryRegionCache<?> c : caches) {
            numFreed += free(c);
        }
        return numFreed;
    }

    private static int free(MemoryRegionCache<?> cache) {
        if (cache == null) {
            return 0;
        }
        return cache.free();
    }

    void trim() {
        trim(tinySubPageHeapCaches);
        trim(smallSubPageHeapCaches);
        trim(normalHeapCaches);
    }

    private static void trim(MemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return;
        }
        for (MemoryRegionCache<?> c : caches) {
            trim(c);
        }
    }

    private static void trim(MemoryRegionCache<?> cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }

    private MemoryRegionCache<?> cacheForTiny(int normCapacity) {
        int idx = PoolArena.tinyIdx(normCapacity);
        return cache(tinySubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForSmall(int normCapacity) {
        int idx = PoolArena.smallIdx(normCapacity);
        return cache(smallSubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForNormal(int normCapacity) {
        int idx = log2(normCapacity >> numShiftsNormalHeap);
        return cache(normalHeapCaches, idx);
    }

    private static <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int idx) {
        if (cache == null || idx > cache.length - 1) {
            return null;
        }
        return cache[idx];
    }

    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     */
    private static final class SubPageMemoryRegionCache<T> extends MemoryRegionCache<T> {
        SubPageMemoryRegionCache(int size) {
            super(size);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBufWithSubpage(buf, handle, reqCapacity);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private static final class NormalMemoryRegionCache<T> extends MemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBuf(buf, handle, reqCapacity);
        }
    }

    /**
     * Cache of {@link PoolChunk} and handles which can be used to allocate a buffer without locking at all.
     */
    private abstract static class MemoryRegionCache<T> {
        private final Entry<T>[] entries;
        private final int maxUnusedCached;
        private int head;
        private int tail;
        private int maxEntriesInUse;
        private int entriesInUse;

        @SuppressWarnings("unchecked")
        MemoryRegionCache(int size) {
            entries = new Entry[powerOfTwo(size)];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new Entry<T>();
            }
            maxUnusedCached = size / 2;
        }

        private static int powerOfTwo(int res) {
            if (res <= 2) {
                return 2;
            }
            res--;
            res |= res >> 1;
            res |= res >> 2;
            res |= res >> 4;
            res |= res >> 8;
            res |= res >> 16;
            res++;
            return res;
        }

        /**
         * Init the {@link PooledByteBuf} using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract void initBuf(PoolChunk<T> chunk, long handle,
                                        PooledByteBuf<T> buf, int reqCapacity);

        /**
         * Add to cache if not already full.
         */
        public boolean add(PoolChunk<T> chunk, long handle) {
            Entry<T> entry = entries[tail];
            if (entry.chunk != null) {
                // cache is full
                return false;
            }
            entriesInUse--;

            entry.chunk = chunk;
            entry.handle = handle;
            tail = nextIdx(tail);
            return true;
        }

        /**
         * Allocate something out of the cache if possible and remove the entry from the cache.
         */
        public boolean allocate(PooledByteBuf<T> buf, int reqCapacity) {
            Entry<T> entry = entries[head];
            if (entry.chunk == null) {
                return false;
            }

            entriesInUse++;
            if (maxEntriesInUse < entriesInUse) {
                maxEntriesInUse = entriesInUse;
            }
            initBuf(entry.chunk, entry.handle, buf, reqCapacity);
            // only null out the chunk as we only use the chunk to check if the buffer is full or not.
            entry.chunk = null;
            head = nextIdx(head);
            return true;
        }

        /**
         * Clear out this cache and free up all previous cached {@link PoolChunk}s and {@code handle}s.
         */
        public int free() {
            int numFreed = 0;
            entriesInUse = 0;
            maxEntriesInUse = 0;
            for (int i = head; ; i = nextIdx(i)) {
                if (freeEntry(entries[i])) {
                    numFreed++;
                } else {
                    // all cleared
                    return numFreed;
                }
            }
        }

        /**
         * Free up cached {@link PoolChunk}s if not allocated frequently enough.
         */
        private void trim() {
            int free = size() - maxEntriesInUse;
            entriesInUse = 0;
            maxEntriesInUse = 0;

            if (free <= maxUnusedCached) {
                return;
            }

            int i = head;
            for (; free > 0; free--) {
                if (!freeEntry(entries[i])) {
                    // all freed
                    break;
                }
                i = nextIdx(i);
            }

            // Update head to point to te correct entry
            // See https://github.com/netty/netty/issues/2924
            head = i;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static boolean freeEntry(Entry entry) {
            PoolChunk chunk = entry.chunk;
            if (chunk == null) {
                return false;
            }
            // need to synchronize on the area from which it was allocated before.
            synchronized (chunk.arena) {
                chunk.parent.free(chunk, entry.handle);
            }
            entry.chunk = null;
            return true;
        }

        /**
         * Return the number of cached entries.
         */
        private int size() {
            return tail - head & entries.length - 1;
        }

        private int nextIdx(int index) {
            // use bitwise operation as this is faster as using modulo.
            return index + 1 & entries.length - 1;
        }

        private static final class Entry<T> {
            PoolChunk<T> chunk;
            long handle;
        }
    }
}
