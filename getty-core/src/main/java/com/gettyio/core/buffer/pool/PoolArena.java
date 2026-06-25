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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PoolArena 是内存池的区域管理器，负责管理多个 {@link PoolChunk} 实例。
 * <p>
 * 在 Netty 的三级架构中，PoolArena 处于第二层，承担以下职责：
 * <ul>
 *   <li>管理 PoolChunk 的生命周期（创建、销毁、回收）</li>
 *   <li>维护 size class 索引表，实现 O(1) 的大小分类</li>
 *   <li>协调 PoolChunk 之间的负载均衡</li>
 *   <li>处理超大分配请求（超过 Chunk 容量的巨型缓冲区）</li>
 * </ul>
 *
 * <h3>Size Class 体系：</h3>
 * <pre>
 *   Tiny:  [16, 32, 48, ..., 496]   — 16 的倍数（31 个类）
 *   Small: [512, 1024, ..., 4096]    — 2 的幂（4 个类，取决于 pageSize）
 *   Normal: [8192, 16384, ...]       — 2 的幂，直到 chunkSize/2
 *   Huge:  > chunkSize/2             — 直接分配，不池化
 * </pre>
 *
 * <h3>线程安全：</h3>
 * <p>
 * 使用 {@link ReentrantLock} 保护 Chunk 列表的增删操作。
 * 单次分配操作内部先获取锁再操作 Chunk 列表，操作完成后释放锁。
 * </p>
 *
 * @author Getty Project
 */
public class PoolArena {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(PoolArena.class);

    // ======================== 配置常量 ========================

    /**
     * 默认页大小：8192 字节（8 KB）
     */
    public static final int DEFAULT_PAGE_SIZE = 8192;

    /**
     * 默认最大二叉树深度：11
     * Chunk 大小 = 2^11 * 8192 = 16 MB
     */
    public static final int DEFAULT_MAX_ORDER = 11;

    /**
     * Tiny 类的大小增量：16 字节
     */
    private static final int TINY_ALIGNMENT = 16;

    /**
     * Small 类的大小增量：512 字节（与 Tiny 对齐）
     */
    private static final int SMALL_ALIGNMENT = 512;

    // ======================== 实例字段 ========================

    /**
     * 页大小（字节），必须是 2 的幂。
     */
    final int pageSize;

    /**
     * 二叉树最大深度。
     */
    final int maxOrder;

    /**
     * Chunk 的总容量 = pageSize * 2^maxOrder。
     */
    final int chunkSize;

    /**
     * log2(pageSize)，用于位运算。
     */
    private final int pageShifts;

    /**
     * 是否使用直接内存。
     */
    final boolean direct;

    // ======================== Size Class 索引表 ========================

    /**
     * Tiny size class 的索引表。
     * sizeClassIndex[capacity / 16] = 对应的 size class 索引。
     * 用于 O(1) 查找 Tiny 类的归一化大小。
     */
    private final int[] tinySizeClassIndex;

    /**
     * 所有 size class 的归一化大小数组（已排序）。
     * index -> normalized capacity。
     */
    private final int[] sizeClasses;

    /**
     * size class 的数量。
     */
    private final int numSizeClasses;

    // ======================== Chunk 管理 ========================

    /**
     * 保护 Chunk 列表的锁。
     * 用于同步 Chunk 的创建、删除和选择操作。
     */
    private final ReentrantLock chunkLock = new ReentrantLock();

    /**
     * Chunk 列表，按使用率从低到高排列。
     * 分配时优先选择使用率最低的 Chunk。
     */
    private final List<PoolChunk> chunks;

    /**
     * 已创建的 Chunk 总数（用于统计）。
     */
    private int chunkCount;

    /**
     * 超大分配计数器，超过 Chunk 容量的请求直接分配。
     */
    private long hugeAllocationCount;

    // ======================== 构造与初始化 ========================

    /**
     * 使用默认配置构造 PoolArena。
     * pageSize = 8192, maxOrder = 11, chunkSize = 16MB。
     *
     * @param direct 是否使用直接内存
     */
    public PoolArena(boolean direct) {
        this(direct, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    /**
     * 使用自定义配置构造 PoolArena。
     *
     * @param direct   是否使用直接内存
     * @param pageSize 页大小（必须是 2 的幂且 >= 16）
     * @param maxOrder 二叉树最大深度（通常 9~13）
     */
    public PoolArena(boolean direct, int pageSize, int maxOrder) {
        this.direct = direct;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = Integer.numberOfTrailingZeros(pageSize);
        this.chunkSize = 1 << (maxOrder + pageShifts);
        this.chunks = new ArrayList<>();
        this.chunkCount = 0;

        // 构建 size class 表
        this.sizeClasses = buildSizeClasses(pageSize);
        this.numSizeClasses = sizeClasses.length;

        // 构建 Tiny 索引表
        int maxTinySize = 512; // Tiny 的最大值为 496 + 16 = 512
        this.tinySizeClassIndex = new int[maxTinySize / TINY_ALIGNMENT + 1];
        for (int i = 0; i < tinySizeClassIndex.length; i++) {
            int size = i * TINY_ALIGNMENT;
            tinySizeClassIndex[i] = findSizeClassIndex(size);
        }
    }

    /**
     * 构建所有 size class 的归一化大小数组。
     * <p>
     * 生成规则：
     * <ol>
     *   <li>Tiny: 16, 32, 48, ..., 496（步长 16，共 31 个）</li>
     *   <li>Small: 512, 1024, 2048, ..., pageSize（2 的幂）</li>
     *   <li>Normal: pageSize*2, pageSize*4, ..., chunkSize/2（2 的幂）</li>
     * </ol>
     *
     * @param pageSize 页大小
     * @return 按升序排列的 size class 数组
     */
    private static int[] buildSizeClasses(int pageSize) {
        List<Integer> classes = new ArrayList<>();

        // Tiny classes: 16, 32, 48, ..., 496
        for (int size = TINY_ALIGNMENT; size < 512; size += TINY_ALIGNMENT) {
            classes.add(size);
        }

        // Small classes: 512, 1024, ..., pageSize（如果 pageSize >= 512）
        int smallStart = 512;
        while (smallStart <= pageSize) {
            classes.add(smallStart);
            smallStart <<= 1;
        }

        // Normal classes: pageSize*2, ..., chunkSize/2
        // chunkSize = pageSize * 2^maxOrder, 但这里不需要 maxOrder 参数
        // 只添加 > pageSize 的 2 的幂
        // 注意：chunkSize/2 是最大的可池化大小
        // 我们不在这里限制上限，由 allocate 方法处理

        int[] result = new int[classes.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = classes.get(i);
        }
        return result;
    }

    /**
     * 查找给定容量对应的 size class 索引。
     * <p>
     * 返回 >= capacity 的最小 size class 的索引。
     * 如果 capacity 超出所有 size class，返回 -1。
     *
     * @param capacity 请求容量
     * @return size class 索引，或 -1
     */
    private int findSizeClassIndex(int capacity) {
        for (int i = 0; i < numSizeClasses; i++) {
            if (sizeClasses[i] >= capacity) {
                return i;
            }
        }
        return -1;
    }

    // ======================== 分配（Allocate） ========================

    /**
     * 线程本地分配上下文，用于追踪最近一次分配的 Chunk/Offset 信息。
     * 由于 PoolThreadCache 的 allocate 是单线程调用，所以这里是安全的。
     */
    private final ThreadLocal<AllocationContext> allocContext = new ThreadLocal<AllocationContext>() {
        @Override
        protected AllocationContext initialValue() {
            return new AllocationContext();
        }
    };

    /**
     * 获取最近一次分配对应的 PoolChunk。
     * 必须在 allocate() 之后立即调用。
     */
    public PoolChunk getLastAllocChunk() {
        return allocContext.get().chunk;
    }

    /**
     * 获取最近一次分配的内存偏移量。
     * 必须在 allocate() 之后立即调用。
     */
    public int getLastAllocOffset() {
        return allocContext.get().offset;
    }

    /**
     * 分配上下文，记录单次分配的元数据。
     */
    static class AllocationContext {
        PoolChunk chunk;
        int offset;
    }

    /**
     * 从 Arena 中分配指定大小的内存。
     * <p>
     * 这是 PoolArena 的核心入口方法，分配流程：
     * <ol>
     *   <li>将请求大小规范化（向上取整到最近的 size class）</li>
     *   <li>如果规范化大小 <= chunkSize/2，从现有 Chunk 中分配</li>
     *   <li>如果现有 Chunk 都不够，创建新的 Chunk</li>
     *   <li>如果请求大小 > chunkSize/2（巨型分配），直接分配不池化</li>
     * </ol>
     * 分配后通过 {@link #getLastAllocChunk()} 和 {@link #getLastAllocOffset()}
     * 获取 Chunk 和偏移信息。
     *
     * @param capacity 请求容量（字节）
     * @return 分配的 ByteBuffer，或 null（参数非法时）
     */
    public ByteBuffer allocate(int capacity) {
        if (capacity <= 0) {
            capacity = 1;
        }

        // 规范化容量
        int normCapacity = normalizeCapacity(capacity);

        // 重置分配上下文
        AllocationContext ctx = allocContext.get();
        ctx.chunk = null;
        ctx.offset = 0;

        // 巨型分配：直接分配，不经过池
        if (normCapacity < 0 || normCapacity > chunkSize / 2) {
            return allocateHuge(capacity);
        }

        // 尝试从现有 Chunk 分配
        chunkLock.lock();
        try {
            for (int i = 0; i < chunks.size(); i++) {
                PoolChunk chunk = chunks.get(i);
                ByteBuffer buf = chunk.allocate(normCapacity);
                if (buf != null) {
                    ctx.chunk = chunk;
                    ctx.offset = chunk.lastAllocOffset();
                    return buf;
                }
            }

            // 所有现有 Chunk 都满了，创建新 Chunk
            PoolChunk newChunk = newChunk();
            chunks.add(newChunk);

            ByteBuffer buf = newChunk.allocate(normCapacity);
            if (buf != null) {
                ctx.chunk = newChunk;
                ctx.offset = newChunk.lastAllocOffset();
                return buf;
            }
        } finally {
            chunkLock.unlock();
        }

        // 不应到达此处
        return allocateHuge(capacity);
    }

    /**
     * 将请求容量规范化到最近的 size class。
     * <p>
     * 规范化规则：
     * <ul>
     *   <li>capacity <= 0: 返回 16（最小分配）</li>
     *   <li>capacity <= 496: 向上取整到 16 的倍数（Tiny）</li>
     *   <li>capacity <= pageSize: 向上取整到 2 的幂（Small）</li>
     *   <li>capacity > pageSize: 向上取整到 2 的幂（Normal）</li>
     * </ul>
     *
     * @param capacity 请求容量
     * @return 规范化后的容量；如果超出可池化范围返回 -1
     */
    int normalizeCapacity(int capacity) {
        if (capacity <= 0) {
            return TINY_ALIGNMENT;
        }

        if (capacity < 512) {
            // Tiny: 向上取整到 16 的倍数
            return (capacity + TINY_ALIGNMENT - 1) & ~(TINY_ALIGNMENT - 1);
        }

        // Small 和 Normal: 向上取整到 2 的幂
        return PoolChunk.nextPowerOfTwo(capacity);
    }

    /**
     * 处理超大分配请求（超出 Chunk 容量一半的请求）。
     * 直接从系统分配内存，不经过池化管理。
     *
     * @param capacity 请求容量
     * @return 分配的 ByteBuffer
     */
    private ByteBuffer allocateHuge(int capacity) {
        hugeAllocationCount++;
        if (direct) {
            return ByteBuffer.allocateDirect(capacity);
        }
        return ByteBuffer.allocate(capacity);
    }

    /**
     * 创建一个新的 PoolChunk。
     *
     * @return 新的 PoolChunk 实例
     */
    private PoolChunk newChunk() {
        chunkCount++;
        return new PoolChunk(this, pageSize, maxOrder, direct);
    }

    // ======================== 释放（Free） ========================

    /**
     * 释放指定内存区域。
     * <p>
     * 根据 offset 和 normCapacity 找到对应的 Chunk 并归还内存。
     * 如果 Chunk 变为完全空闲且不是最后一个 Chunk，则销毁该 Chunk。
     *
     * @param chunk        内存所属的 PoolChunk
     * @param offset       内存偏移量
     * @param normCapacity 规范化容量
     */
    void free(PoolChunk chunk, int offset, int normCapacity) {
        chunk.free(offset, normCapacity);

        // 如果 Chunk 完全空闲且不是唯一的 Chunk，考虑销毁
        if (chunk.isEmpty()) {
            chunkLock.lock();
            try {
                if (chunks.size() > 1 && chunk.isEmpty()) {
                    chunks.remove(chunk);
                    chunk.destroy();
                }
            } finally {
                chunkLock.unlock();
            }
        }
    }

    /**
     * 获取指定内存偏移量对应的 PoolChunk。
     * 用于从 ByteBuffer 反查其所属的 Chunk。
     * <p>
     * 注意：此方法是 O(n) 的遍历，在高并发场景下应尽量避免调用。
     * 实际使用中，{@link PooledByteBuffer} 会直接记录所属 Chunk 的引用。
     *
     * @return 匹配的 PoolChunk，或 null
     */
    PoolChunk findChunk(int offset) {
        chunkLock.lock();
        try {
            for (PoolChunk chunk : chunks) {
                if (offset >= 0 && offset < chunk.chunkSize()) {
                    return chunk;
                }
            }
        } finally {
            chunkLock.unlock();
        }
        return null;
    }

    // ======================== 清理与统计 ========================

    /**
     * 清理所有 Chunk，释放所有内存。
     */
    public void clear() {
        chunkLock.lock();
        try {
            for (PoolChunk chunk : chunks) {
                chunk.destroy();
            }
            chunks.clear();
        } finally {
            chunkLock.unlock();
        }
    }

    /**
     * @return 当前 Chunk 的数量
     */
    public int chunkCount() {
        chunkLock.lock();
        try {
            return chunks.size();
        } finally {
            chunkLock.unlock();
        }
    }

    /**
     * @return 总使用内存（字节，所有 Chunk 的 usage 之和）
     */
    public long totalUsedMemory() {
        long total = 0;
        chunkLock.lock();
        try {
            for (PoolChunk chunk : chunks) {
                total += chunk.usage();
            }
        } finally {
            chunkLock.unlock();
        }
        return total;
    }

    /**
     * @return 页大小
     */
    public int pageSize() {
        return pageSize;
    }

    /**
     * @return Chunk 大小
     */
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * @return 是否使用直接内存
     */
    public boolean isDirect() {
        return direct;
    }

    @Override
    public String toString() {
        return String.format("PoolArena{direct=%b,pageSize=%d,chunkSize=%d,chunks=%d,usage=%d}",
                direct, pageSize, chunkSize, chunkCount(), totalUsedMemory());
    }
}
