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
import com.gettyio.core.util.queue.MpscLinkedQueue;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * PoolThreadCache 是内存池三级架构的最顶层，提供线程本地缓存。
 * <p>
 * 每个线程拥有独立的 PoolThreadCache 实例，缓存最近释放的 {@link PooledByteBuffer}，
 * 使得同一线程的后续分配可以无锁地从缓存中获取，大幅减少线程竞争。
 * </p>
 *
 * <h3>工作原理：</h3>
 * <pre>
 *   分配（allocate）：
 *     1. 先查找线程本地缓存（对应 size class 的栈）
 *     2. 缓存命中 → 直接返回，无锁操作 ← 最快路径
 *     3. 缓存未命中 → 从 PoolArena 分配 ← 需要锁
 *
 *   释放（release）：
 *     1. 将 ByteBuffer 推入当前线程的本地缓存栈
 *     2. 缓存已满 → 归还给 PoolArena ← 需要锁
 * </pre>
 *
 * <h3>缓存大小策略：</h3>
 * <p>
 * 每个 size class 的缓存栈大小不同：
 * <ul>
 *   <li>Tiny (<= 496B): 最大缓存 256 个（高频小缓冲区）</li>
 *   <li>Small (512B ~ 4KB): 最大缓存 128 个</li>
 *   <li>Normal (8KB ~ 64KB): 最大缓存 64 个</li>
 *   <li>Large (> 64KB): 最大缓存 16 个</li>
 * </ul>
 * </p>
 *
 * <h3>线程安全：</h3>
 * <p>
 * PoolThreadCache 本身是线程私有的，所有操作不需要加锁。
 * 仅当缓存未命中或缓存已满时才访问共享的 PoolArena（由 Arena 内部加锁）。
 * </p>
 *
 * @author Getty Project
 */
public class PoolThreadCache {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(PoolThreadCache.class);

    // ======================== 配置常量 ========================

    /**
     * Tiny size class 的最大缓存数量。
     */
    private static final int MAX_TINY_CACHE_SIZE = 256;

    /**
     * Small size class 的最大缓存数量。
     */
    private static final int MAX_SMALL_CACHE_SIZE = 128;

    /**
     * Normal size class 的最大缓存数量。
     */
    private static final int MAX_NORMAL_CACHE_SIZE = 64;

    /**
     * Large size class 的最大缓存数量。
     */
    private static final int MAX_LARGE_CACHE_SIZE = 16;

    /**
     * Tiny 对齐大小：16 字节。
     * 所有 Tiny 级别的容量都是 16 的倍数。
     */
    private static final int TINY_ALIGNMENT = 16;

    /**
     * Tiny/Small 的边界：512 字节。
     */
    private static final int TINY_BOUNDARY = 512;

    /**
     * Small/Normal 的边界：8192 字节。
     */
    private static final int SMALL_BOUNDARY = 8192;

    /**
     * Normal/Large 的边界：65536 字节（64 KB）。
     */
    private static final int NORMAL_BOUNDARY = 65536;

    // ======================== Size Class 缓存队列 ========================

    /**
     * Tiny size class 的缓存栈数组。
     * 索引 i 对应容量 (i+1)*16 字节的 ByteBuffer。
     * 共 31 个槽位（16, 32, 48, ..., 496）。
     */
    private final Deque<CacheEntry>[] tinyCaches;

    /**
     * Small/Normal/Large size class 的缓存栈数组。
     * 索引 i 对应容量 2^(i+9) 字节的 ByteBuffer（512, 1024, 2048, ...）。
     * 共 20 个槽位（512B ~ 256MB）。
     */
    private final Deque<CacheEntry>[] normalCaches;

    /**
     * 所属的 PoolArena。
     * 缓存未命中时从这里分配，缓存已满时归还到这里。
     */
    final PoolArena heapArena;

    /**
     * 所属的直接内存 PoolArena（如果有的话）。
     */
    final PoolArena directArena;

    /**
     * 缓存命中率统计（用于调试和监控）。
     */
    private long cacheHitCount;

    /**
     * 缓存未命中率统计。
     */
    private long cacheMissCount;

    // ======================== 跨线程回收 ========================

    /**
     * 创建此 PoolThreadCache 的线程。
     * 跨线程释放时通过此字段判断是否走 MPSC 队列。
     */
    private final Thread ownerThread;

    /**
     * 跨线程回收队列（MPSC，无锁）。
     * 其他线程调用 {@link #crossThreadRecycle(CacheEntry)} 时将 CacheEntry 入此队列，
     * owner 线程在 {@link #allocate(int, boolean)} 前批量 drain 到本地缓存。
     */
    private final MpscLinkedQueue<CacheEntry> crossThreadRecycleQueue = new MpscLinkedQueue<>();

    // ======================== 构造 ========================

    /**
     * 构造 PoolThreadCache。
     *
     * @param heapArena   堆内存 Arena
     * @param directArena 直接内存 Arena（可为 null）
     */
    @SuppressWarnings("unchecked")
    public PoolThreadCache(PoolArena heapArena, PoolArena directArena) {
        this.heapArena = heapArena;
        this.directArena = directArena;
        this.ownerThread = Thread.currentThread();

        // 初始化 Tiny 缓存：31 个槽位（16B, 32B, ..., 496B）
        this.tinyCaches = new Deque[31];
        for (int i = 0; i < tinyCaches.length; i++) {
            tinyCaches[i] = new ArrayDeque<>(MAX_TINY_CACHE_SIZE);
        }

        // 初始化 Normal 缓存：20 个槽位（512B, 1024B, ..., 256MB）
        this.normalCaches = new Deque[20];
        for (int i = 0; i < normalCaches.length; i++) {
            normalCaches[i] = new ArrayDeque<>(getMaxCacheSize(1 << (i + 9)));
        }
    }

    // ======================== 分配（Allocate） ========================

    /**
     * 从线程缓存中分配指定大小的 ByteBuffer。
     * <p>
     * 这是最常用的高频调用路径。如果缓存命中，直接返回缓存的 ByteBuffer（零延迟）。
     * 如果缓存未命中，回退到 PoolArena 分配。
     * <p>
     * 分配后通过 {@link #getLastChunk()} 和 {@link #getLastOffset()} 获取 Chunk 和偏移信息，
     * 用于创建 PooledByteBuffer 时记录来源。
     *
     * @param capacity 请求容量（字节）
     * @param direct   是否使用直接内存
     * @return 分配的 ByteBuffer（已 clear，position=0, limit=capacity）
     */
    public ByteBuffer allocate(int capacity, boolean direct) {
        // 批量接收其他线程跨线程归还的缓冲区（owner 线程执行，无锁）
        drainCrossThreadQueue();

        int normCapacity = normalizeCapacity(capacity);
        int cacheIndex = getCacheIndex(normCapacity);

        // 从线程缓存尝试获取（快速路径，零开销）
        CacheEntry entry = popFromCache(cacheIndex, normCapacity);
        if (entry != null) {
            cacheHitCount++;
            lastChunk = entry.chunk;
            lastOffset = entry.offset;
            ByteBuffer buf = entry.buffer;
            buf.clear();
            return buf;
        }

        // 缓存未命中，从 Arena 分配
        cacheMissCount++;
        PoolArena arena = direct ? (directArena != null ? directArena : heapArena) : heapArena;
        ByteBuffer buf = arena.allocate(capacity);
        if (buf != null) {
            lastChunk = arena.getLastAllocChunk();
            lastOffset = arena.getLastAllocOffset();
            buf.clear();
        }
        return buf;
    }

    // ======================== 分配元数据访问 ========================

    /**
     * 最近一次分配的 PoolChunk（缓存命中时为原缓存的 chunk，未命中时为 Arena 的 chunk）。
     * 必须在 allocate() 之后立即调用。
     */
    private PoolChunk lastChunk;

    /**
     * 最近一次分配的内存偏移量。
     * 必须在 allocate() 之后立即调用。
     */
    private int lastOffset;

    public PoolChunk getLastChunk() {
        return lastChunk;
    }

    public int getLastOffset() {
        return lastOffset;
    }

    // ======================== 释放（Release/Recycle） ========================

    /**
     * 将 ByteBuffer 归还到线程缓存。
     * <p>
     * 如果对应的缓存栈未满，将 ByteBuffer 推入栈中（快速路径）。
     * 如果缓存已满，将 ByteBuffer 归还给 PoolArena（慢速路径）。
     *
     * @param buffer   要归还的 ByteBuffer
     * @param chunk    所属的 PoolChunk（可为 null，表示非池化缓冲区）
     * @param offset   在 Chunk 中的偏移量
     * @param normCap  规范化容量
     */
    public void recycle(ByteBuffer buffer, PoolChunk chunk, int offset, int normCap) {
        int cacheIndex = getCacheIndex(normCap);

        // 尝试推入缓存栈
        boolean cached = pushToCache(cacheIndex, buffer, chunk, offset, normCap);
        if (!cached && chunk != null) {
            // 缓存已满，归还给 Arena
            PoolArena arena = buffer.isDirect() ? (directArena != null ? directArena : heapArena) : heapArena;
            arena.free(chunk, offset, normCap);
        }
    }

    // ======================== 缓存操作 ========================

    /**
     * 从缓存栈中弹出一个条目。
     *
     * @param cacheIndex   缓存槽位索引
     * @param normCapacity 请求的规范化容量
     * @return 缓存条目，或 null（缓存为空）
     */
    private CacheEntry popFromCache(int cacheIndex, int normCapacity) {
        Deque<CacheEntry> cache = getCache(cacheIndex);
        if (cache == null) {
            return null;
        }

        // 从栈顶弹出（最近释放的，CPU 缓存友好）
        while (!cache.isEmpty()) {
            CacheEntry entry = cache.pollLast();
            if (entry != null && entry.buffer != null && entry.buffer.capacity() >= normCapacity) {
                return entry;
            }
            // 容量不匹配的条目直接丢弃（归还给 Arena）
            if (entry != null && entry.chunk != null) {
                PoolArena arena = entry.buffer.isDirect()
                        ? (directArena != null ? directArena : heapArena) : heapArena;
                arena.free(entry.chunk, entry.offset, entry.normCapacity);
            }
        }
        return null;
    }

    /**
     * 将一个条目推入缓存栈。
     *
     * @param cacheIndex   缓存槽位索引
     * @param buffer       ByteBuffer
     * @param chunk        所属 Chunk
     * @param offset       偏移量
     * @param normCapacity 规范化容量
     * @return true 如果成功推入，false 如果缓存已满
     */
    private boolean pushToCache(int cacheIndex, ByteBuffer buffer, PoolChunk chunk,
                                int offset, int normCapacity) {
        Deque<CacheEntry> cache = getCache(cacheIndex);
        if (cache == null) {
            return false;
        }

        int maxSize = getMaxCacheSize(normCapacity);
        if (cache.size() >= maxSize) {
            return false;
        }

        cache.addLast(new CacheEntry(buffer, chunk, offset, normCapacity));
        return true;
    }

    /**
     * 获取指定容量对应的缓存队列。
     *
     * @param cacheIndex 缓存索引（由 {@link #getCacheIndex(int)} 计算）
     * @return 缓存队列，或 null（容量超出缓存范围）
     */
    private Deque<CacheEntry> getCache(int cacheIndex) {
        if (cacheIndex < 0) {
            return null;
        }
        if (cacheIndex < 31) {
            // Tiny
            return tinyCaches[cacheIndex];
        }
        int normalIndex = cacheIndex - 31;
        if (normalIndex < normalCaches.length) {
            return normalCaches[normalIndex];
        }
        return null;
    }

    // ======================== 索引与大小计算 ========================

    /**
     * 计算容量对应的缓存索引。
     * <p>
     * 索引分配：
     * <ul>
     *   <li>[0, 30]: Tiny（16B ~ 496B），index = capacity/16 - 1</li>
     *   <li>[31, 50]: Small/Normal/Large（512B+），index = 31 + log2(capacity/512)</li>
     * </ul>
     *
     * @param normCapacity 规范化容量
     * @return 缓存索引
     */
    int getCacheIndex(int normCapacity) {
        if (normCapacity <= 0) {
            return 0;
        }
        if (normCapacity < TINY_BOUNDARY) {
            // Tiny: index = capacity/16 - 1
            return (normCapacity / TINY_ALIGNMENT) - 1;
        }
        // Small/Normal/Large: index = 31 + log2(capacity/512)
        int log2 = 31 - Integer.numberOfLeadingZeros(normCapacity / TINY_BOUNDARY);
        return 31 + log2;
    }

    /**
     * 规范化容量：向上取整到最近的 size class。
     *
     * @param capacity 原始容量
     * @return 规范化容量
     */
    static int normalizeCapacity(int capacity) {
        if (capacity <= 0) {
            return TINY_ALIGNMENT;
        }
        if (capacity < TINY_BOUNDARY) {
            // Tiny: 向上取整到 16 的倍数
            return (capacity + TINY_ALIGNMENT - 1) & ~(TINY_ALIGNMENT - 1);
        }
        // Small/Normal/Large: 向上取整到 2 的幂
        return PoolChunk.nextPowerOfTwo(capacity);
    }

    /**
     * 获取指定 size class 的最大缓存数量。
     *
     * @param normCapacity 规范化容量
     * @return 最大缓存条目数
     */
    private int getMaxCacheSize(int normCapacity) {
        if (normCapacity < TINY_BOUNDARY) {
            return MAX_TINY_CACHE_SIZE;
        }
        if (normCapacity < SMALL_BOUNDARY) {
            return MAX_SMALL_CACHE_SIZE;
        }
        if (normCapacity < NORMAL_BOUNDARY) {
            return MAX_NORMAL_CACHE_SIZE;
        }
        return MAX_LARGE_CACHE_SIZE;
    }

    // ======================== 清理 ========================

    /**
     * 清空所有线程缓存，将缓存中的 ByteBuffer 归还给 PoolArena。
     * <p>
     * 通常在池关闭或线程退出时调用。
     * </p>
     */
    public void free() {
        // 清空 Tiny 缓存
        for (Deque<CacheEntry> cache : tinyCaches) {
            clearCache(cache);
        }
        // 清空 Normal 缓存
        for (Deque<CacheEntry> cache : normalCaches) {
            clearCache(cache);
        }
    }

    /**
     * 清空单个缓存队列，将所有条目归还给 Arena。
     */
    private void clearCache(Deque<CacheEntry> cache) {
        while (!cache.isEmpty()) {
            CacheEntry entry = cache.pollFirst();
            if (entry != null && entry.chunk != null) {
                PoolArena arena = entry.buffer.isDirect()
                        ? (directArena != null ? directArena : heapArena) : heapArena;
                arena.free(entry.chunk, entry.offset, entry.normCapacity);
            }
        }
    }

    // ======================== 跨线程回收 API ========================

    /**
     * 判断当前线程是否是此 PoolThreadCache 的所有者。
     * <p>
     * 用于 {@link PooledByteBuffer#release()} 判断走快速路径还是跨线程 MPSC 队列。
     * </p>
     *
     * @return true 如果当前线程是 owner 线程
     */
    public boolean isOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    /**
     * 跨线程回收缓冲区。
     * <p>
     * 由非 owner 线程调用（如 AIO 回调线程释放业务线程分配的缓冲区），
     * 通过 MPSC 队列将 CacheEntry 安全传递给 owner 线程。
     * owner 线程在下次 {@link #allocate(int, boolean)} 时批量 drain 回本地缓存。
     * </p>
     * <p>
     * 成本：~10-20ns（一次 CAS 操作）。
     * </p>
     *
     * @param entry 待回收的缓存条目
     */
    public void crossThreadRecycle(CacheEntry entry) {
        crossThreadRecycleQueue.offer(entry);
    }

    /**
     * 批量 drain 跨线程回收队列到本地缓存。
     * <p>
     * 必须由 owner 线程调用。在 {@link #allocate(int, boolean)} 开始时调用，
     * 将其他线程归还的缓冲区批量移入本地 ArrayDeque，避免每次都走 MPSC。
     * </p>
     */
    private void drainCrossThreadQueue() {
        crossThreadRecycleQueue.drainTo(entry -> {
            int cacheIndex = getCacheIndex(entry.normCapacity);
            boolean cached = pushToCache(cacheIndex, entry.buffer, entry.chunk,
                    entry.offset, entry.normCapacity);
            if (!cached && entry.chunk != null) {
                // 本地缓存已满，归还给 Arena（owner 线程执行，无锁问题）
                PoolArena arena = entry.buffer.isDirect()
                        ? (directArena != null ? directArena : heapArena) : heapArena;
                arena.free(entry.chunk, entry.offset, entry.normCapacity);
            }
        });
    }

    // ======================== 统计 ========================

    /**
     * @return 缓存命中次数
     */
    public long cacheHitCount() {
        return cacheHitCount;
    }

    /**
     * @return 缓存未命中次数
     */
    public long cacheMissCount() {
        return cacheMissCount;
    }

    /**
     * @return 缓存命中率（0.0 ~ 1.0）
     */
    public double cacheHitRate() {
        long total = cacheHitCount + cacheMissCount;
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHitCount / total;
    }

    @Override
    public String toString() {
        return String.format("PoolThreadCache{hit=%d,miss=%d,rate=%.1f%%}",
                cacheHitCount, cacheMissCount, cacheHitRate() * 100);
    }

    // ======================== 内部数据结构 ========================

    /**
     * 缓存条目，记录 ByteBuffer 及其元数据。
     * <p>
     * 当 ByteBuffer 被缓存到线程本地时，需要记住它来自哪个 Chunk、
     * 偏移量是多少，以便在缓存被淘汰时能正确归还。
     * </p>
     */
    static class CacheEntry {
        /**
         * 缓存的 ByteBuffer。
         */
        final ByteBuffer buffer;

        /**
         * ByteBuffer 所属的 PoolChunk（可能为 null，表示非池化缓冲区）。
         */
        final PoolChunk chunk;

        /**
         * 在 Chunk 中的内存偏移量。
         */
        final int offset;

        /**
         * 分配时的规范化容量。
         */
        final int normCapacity;

        CacheEntry(ByteBuffer buffer, PoolChunk chunk, int offset, int normCapacity) {
            this.buffer = buffer;
            this.chunk = chunk;
            this.offset = offset;
            this.normCapacity = normCapacity;
        }
    }
}
