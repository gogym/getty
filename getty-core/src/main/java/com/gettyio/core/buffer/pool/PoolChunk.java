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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PoolChunk 是内存池的核心数据结构，管理一块连续的大内存区域。
 * <p>
 * 设计灵感来源于 Netty 的 PoolChunk，使用完全二叉树（Complete Binary Tree）来管理内存分配。
 * 树的每个节点存储其子树中最大可用连续子页数量，从而实现 O(log N) 的分配/释放性能。
 * </p>
 *
 * <h3>核心设计原理：</h3>
 * <ul>
 *   <li>Chunk 的总容量 = 2^maxOrder * pageSize</li>
 *   <li>叶子节点（tree depth = maxOrder）每个代表一个 page（默认 8192 字节）</li>
 *   <li>内部节点值 = max(leftChild, rightChild)，表示子树中最大可用连续页数</li>
 *   <li>分配时从根节点向下搜索合适的空闲节点</li>
 *   <li>对于小于 pageSize 的分配，使用位图（bitmap）进行子页级管理</li>
 * </ul>
 *
 * <h3>线程安全：</h3>
 * <p>
 * 所有分配和释放操作均通过 synchronized 保证线程安全。
 * 在高并发场景下，应优先从 {@link PoolThreadCache} 获取缓冲区，
 * 仅在缓存未命中时才访问 PoolChunk，以此减少锁竞争。
 * </p>
 *
 * @author Getty Project
 */
public class PoolChunk {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(PoolChunk.class);

    /**
     * 页大小，默认 8192 字节。
     * 所有小于等于此大小的分配请求将使用子页分配（位图管理）。
     */
    private final int pageSize;

    /**
     * 二叉树的最大深度（从 0 开始计数）。
     * maxOrder = 11 时，Chunk 总容量 = 2^11 * 8192 = 16MB。
     */
    private final int maxOrder;

    /**
     * Chunk 管理的总内存容量（字节），等于 pageSize * 2^maxOrder。
     */
    private final int chunkSize;

    /**
     * log2(pageSize) 的值，用于位运算快速计算页索引。
     */
    private final int pageShifts;

    /**
     * 当前是否使用直接内存（堆外内存）。
     * 直接内存可减少 JVM 堆内存压力，适用于 I/O 密集型场景。
     */
    private final boolean direct;

    /**
     * 底层内存存储。
     * <ul>
     *   <li>direct=true 时使用堆外直接内存（DirectByteBuffer）</li>
     *   <li>direct=false 时使用堆内 byte[] 数组</li>
     * </ul>
     */
    private final ByteBuffer memory;

    // ======================== 二叉树管理 ========================

    /**
     * 完全二叉树数组，采用堆式存储（heap storage）：
     * <ul>
     *   <li>节点 i 的左子节点 = 2i，右子节点 = 2i + 1</li>
     *   <li>根节点在索引 1（索引 0 不使用）</li>
     *   <li>tree[i] = 该子树中最大可用连续子页数量</li>
     * </ul>
     * <p>
     * 例如：tree[1] = 2^maxOrder 表示整个 Chunk 完全空闲。
     */
    private final int[] tree;

    /**
     * 树数组的长度，等于 2^(maxOrder+1)。
     */
    private final int treeLength;

    // ======================== 子页分配管理 ========================

    /**
     * 子页位图映射表。
     * key = 叶子节点索引（从 2^maxOrder 到 2^(maxOrder+1)-1）
     * value = 该页的位图数组，每个 bit 代表一个 slot 的使用状态。
     */
    private final ConcurrentHashMap<Integer, AtomicInteger[]> subpageBitmaps;

    /**
     * 每个叶子页当前已分配的 slot 数量。
     * key = 叶子节点索引, value = 已分配数。
     */
    private final ConcurrentHashMap<Integer, AtomicInteger> pageAllocationCount;

    /**
     * 每个叶子页被分配时对应的 norm 大小。
     * 确保同一页只服务于相同大小的分配请求。
     * key = 叶子节点索引, value = 分配的规范化大小。
     */
    private final ConcurrentHashMap<Integer, Integer> pageSizeMap;

    // ======================== Chunk 状态 ========================

    /**
     * 已使用的字节数（近似值），用于统计和调试。
     */
    private final AtomicInteger usage;

    /**
     * 最后一次分配的内存偏移量。
     * 由 allocate 方法设置，由 PoolArena 通过 lastAllocOffset() 获取。
     */
    private volatile int lastAllocOffset;

    /**
     * 所属的 PoolArena 引用，用于归还和统计。
     */
    final PoolArena parent;

    /**
     * 链表指针：Chunk 在 Arena 的 PoolChunkList 中形成双向链表。
     */
    PoolChunk prev;
    PoolChunk next;

    // ======================== 构造与初始化 ========================

    /**
     * 构造一个新的 PoolChunk 实例。
     *
     * @param parent   所属的 PoolArena，可以为 null（独立使用时）
     * @param pageSize 页大小（字节），必须是 2 的幂
     * @param maxOrder 二叉树最大深度，Chunk 容量 = pageSize * 2^maxOrder
     * @param direct   是否使用直接内存
     */
    public PoolChunk(PoolArena parent, int pageSize, int maxOrder, boolean direct) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = log2(pageSize);
        this.chunkSize = 1 << (maxOrder + pageShifts);
        this.direct = direct;
        this.usage = new AtomicInteger(0);

        // 分配底层内存
        if (direct) {
            this.memory = ByteBuffer.allocateDirect(chunkSize);
        } else {
            this.memory = ByteBuffer.allocate(chunkSize);
        }

        // 初始化完全二叉树
        // 树的大小 = 2^(maxOrder+1)，索引 0 不使用
        this.treeLength = 1 << (maxOrder + 1);
        this.tree = new int[treeLength];
        initTree();

        // 初始化子页管理结构
        this.subpageBitmaps = new ConcurrentHashMap<>();
        this.pageAllocationCount = new ConcurrentHashMap<>();
        this.pageSizeMap = new ConcurrentHashMap<>();
    }

    /**
     * 初始化完全二叉树。
     * <p>
     * 从叶子节点向上构建：
     * <ol>
     *   <li>每个叶子节点初始值为 1（代表 1 个空闲页）</li>
     *   <li>每个内部节点 = max(左子节点, 右子节点)</li>
     * </ol>
     * 初始化完成后，tree[1]（根节点）= 2^maxOrder，表示所有页均空闲。
     */
    private void initTree() {
        int leafStart = 1 << maxOrder;
        int leafEnd = 1 << (maxOrder + 1);

        // 叶子节点：每个叶子代表一个空闲页，值为 1
        for (int i = leafStart; i < leafEnd; i++) {
            tree[i] = 1;
        }

        // 自底向上构建内部节点
        for (int i = leafStart - 1; i >= 1; i--) {
            tree[i] = Math.max(tree[2 * i], tree[2 * i + 1]);
        }
    }

    // ======================== 分配（Allocate） ========================

    /**
     * 从 Chunk 中分配指定大小的内存。
     * <p>
     * 分配流程：
     * <ol>
     *   <li>将请求大小向上取整到最近的 2 的幂</li>
     *   <li>如果 normSize <= pageSize，使用子页分配（位图管理）</li>
     *   <li>如果 normSize > pageSize，使用二叉树分配整页</li>
     * </ol>
     *
     * @param normCapacity 请求的规范化容量（字节）
     * @return 分配的 ByteBuffer 视图，容量不足时返回 null
     */
    synchronized ByteBuffer allocate(int normCapacity) {
        if (normCapacity > chunkSize) {
            return null;
        }

        if (normCapacity <= pageSize) {
            return allocateSubpage(normCapacity);
        } else {
            return allocatePage(normCapacity);
        }
    }

    /**
     * 整页分配（normCapacity >= pageSize）。
     * <p>
     * 使用二叉树从根节点向下搜索，找到恰好容纳请求大小的空闲区域。
     * 搜索策略类似二分查找：如果左子树有足够的空间就走左边，否则走右边。
     *
     * @param normCapacity 规范化容量
     * @return 分配的 ByteBuffer 切片，或 null
     */
    private ByteBuffer allocatePage(int normCapacity) {
        // 计算需要的连续空闲页数
        int needPages = normCapacity >>> pageShifts;
        if (needPages == 0) {
            needPages = 1;
        }

        // 根节点的最大可用页数不足，分配失败
        if (tree[1] < needPages) {
            return null;
        }

        // 从根节点向下搜索合适的分配位置
        int node = 1;
        int depth = 0;

        while (depth < maxOrder) {
            int left = node << 1;
            int right = left | 1;

            if (tree[left] >= needPages) {
                node = left;
            } else if (tree[right] >= needPages) {
                node = right;
            } else {
                // 理论上不应到达这里，因为已经检查了 tree[1]
                return null;
            }
            depth++;
        }

        // 到达叶子节点，执行分配
        int leafNode = node;
        tree[leafNode] = 0;

        // 计算内存偏移量并创建视图
        int offset = (leafNode - (1 << maxOrder)) * pageSize;
        lastAllocOffset = offset;
        ByteBuffer slice = createSlice(offset, normCapacity);

        // 更新路径上的树节点
        updateTreeAfterAllocate(leafNode);

        // 标记此页为整页分配（非子页）
        pageAllocationCount.put(leafNode, new AtomicInteger(1));
        pageSizeMap.put(leafNode, normCapacity);

        usage.addAndGet(normCapacity);
        return slice;
    }

    /**
     * 子页分配（normCapacity < pageSize）。
     * <p>
     * 首先在二叉树中找到一个空闲叶子页，然后在该页内使用位图分配 slot。
     * 如果某个叶子页已经部分分配且 norm 大小匹配，则复用该页。
     *
     * @param normCapacity 规范化容量（必须 <= pageSize）
     * @return 分配的 ByteBuffer 切片，或 null
     */
    private ByteBuffer allocateSubpage(int normCapacity) {
        int leafStart = 1 << maxOrder;
        int leafEnd = 1 << (maxOrder + 1);
        int slotsPerPage = pageSize / normCapacity;

        // 遍历所有叶子节点，寻找：
        // 1. 已分配给相同 norm 大小且有空闲 slot 的页
        // 2. 完全空闲的页
        for (int leaf = leafStart; leaf < leafEnd; leaf++) {
            AtomicInteger count = pageAllocationCount.get(leaf);
            Integer norm = pageSizeMap.get(leaf);

            if (count != null && norm != null && norm == normCapacity) {
                // 该页已用于相同大小的分配，尝试分配 slot
                if (count.get() < slotsPerPage) {
                    int slot = allocateSlot(leaf, slotsPerPage);
                    if (slot >= 0) {
                        int offset = (leaf - leafStart) * pageSize + slot * normCapacity;
                        count.incrementAndGet();
                        usage.addAndGet(normCapacity);
                        lastAllocOffset = offset;
                        return createSlice(offset, normCapacity);
                    }
                }
            }
        }

        // 没有找到部分使用的匹配页，寻找完全空闲的叶子
        if (tree[1] < 1) {
            return null;
        }

        // 从根节点搜索空闲叶子
        int node = 1;
        int depth = 0;
        while (depth < maxOrder) {
            int left = node << 1;
            int right = left | 1;
            if (tree[left] >= 1) {
                node = left;
            } else if (tree[right] >= 1) {
                node = right;
            } else {
                return null;
            }
            depth++;
        }

        int leafNode = node;

        // 初始化该页的位图
        int bitmapSize = (slotsPerPage + 31) >> 5; // 每个 int 存储 32 个 bit
        AtomicInteger[] bitmap = new AtomicInteger[bitmapSize];
        for (int i = 0; i < bitmapSize; i++) {
            bitmap[i] = new AtomicInteger(0);
        }
        subpageBitmaps.put(leafNode, bitmap);

        // 分配第 0 个 slot
        bitmap[0].set(1); // 标记 slot 0 为已使用
        pageAllocationCount.put(leafNode, new AtomicInteger(1));
        pageSizeMap.put(leafNode, normCapacity);

        // 将叶子节点标记为已使用（tree 值设为 0）
        tree[leafNode] = 0;
        updateTreeAfterAllocate(leafNode);

        int offset = (leafNode - leafStart) * pageSize;
        usage.addAndGet(normCapacity);
        lastAllocOffset = offset;
        return createSlice(offset, normCapacity);
    }

    /**
     * 在指定叶子页中分配一个空闲 slot。
     * <p>
     * 使用 CAS 操作修改位图，保证并发安全。
     *
     * @param leafIndex    叶子节点索引
     * @param slotsPerPage 该页的 slot 总数
     * @return 分配的 slot 索引，-1 表示没有空闲 slot
     */
    private int allocateSlot(int leafIndex, int slotsPerPage) {
        AtomicInteger[] bitmap = subpageBitmaps.get(leafIndex);
        if (bitmap == null) {
            return -1;
        }

        for (int i = 0; i < bitmap.length; i++) {
            int word = bitmap[i].get();
            // 如果当前 word 还有空闲位（不是所有 32 位都被占用）
            while (word != -1) { // -1 = 0xFFFFFFFF = 所有位都被占用
                // 找到第一个为 0 的位
                int bit = Integer.numberOfTrailingZeros(~word);
                int slotIndex = (i << 5) + bit;
                if (slotIndex >= slotsPerPage) {
                    return -1;
                }
                // 尝试通过 CAS 标记该位
                if (bitmap[i].compareAndSet(word, word | (1 << bit))) {
                    return slotIndex;
                }
                // CAS 失败，读取最新值重试
                word = bitmap[i].get();
            }
        }
        return -1;
    }

    // ======================== 释放（Free） ========================

    /**
     * 释放之前分配的内存。
     * <p>
     * 释放流程：
     * <ol>
     *   <li>根据内存偏移量计算叶子节点索引</li>
     *   <li>如果是子页分配，清除对应的位图 bit</li>
     *   <li>如果该页所有 slot 都已释放，将页归还给二叉树</li>
     *   <li>如果是整页分配，直接将页归还给二叉树</li>
     *   <li>向上更新二叉树路径</li>
     * </ol>
     *
     * @param offset       内存偏移量（字节）
     * @param normCapacity 分配时使用的规范化容量
     */
    synchronized void free(int offset, int normCapacity) {
        int leafStart = 1 << maxOrder;

        if (normCapacity <= pageSize) {
            // 子页释放
            int pageIndex = offset >>> pageShifts;
            int leafNode = leafStart + pageIndex;
            int slotOffset = offset - (pageIndex << pageShifts);
            int slotIndex = slotOffset / normCapacity;

            AtomicInteger[] bitmap = subpageBitmaps.get(leafNode);
            if (bitmap != null) {
                int wordIndex = slotIndex >>> 5;
                int bitIndex = slotIndex & 31;
                // 清除对应的 bit
                bitmap[wordIndex].getAndUpdate(w -> w & ~(1 << bitIndex));
            }

            AtomicInteger count = pageAllocationCount.get(leafNode);
            if (count != null && count.decrementAndGet() == 0) {
                // 该页所有 slot 都已释放，归还整页
                subpageBitmaps.remove(leafNode);
                pageAllocationCount.remove(leafNode);
                pageSizeMap.remove(leafNode);
                tree[leafNode] = 1;
                updateTreeAfterFree(leafNode);
            }
        } else {
            // 整页释放
            int pageIndex = offset >>> pageShifts;
            int leafNode = leafStart + pageIndex;
            pageAllocationCount.remove(leafNode);
            pageSizeMap.remove(leafNode);
            tree[leafNode] = 1;
            updateTreeAfterFree(leafNode);
        }

        usage.addAndGet(-normCapacity);
    }

    // ======================== 树更新 ========================

    /**
     * 分配后向上更新二叉树。
     * <p>
     * 从指定节点的父节点开始，逐层向上更新每个节点的值为
     * max(左子节点, 右子节点)，直到根节点。
     *
     * @param node 刚被分配的叶子/子树节点
     */
    private void updateTreeAfterAllocate(int node) {
        int parent = node >>> 1;
        while (parent >= 1) {
            tree[parent] = Math.max(tree[parent << 1], tree[(parent << 1) | 1]);
            parent >>>= 1;
        }
    }

    /**
     * 释放后向上更新二叉树。
     * <p>
     * 与 {@link #updateTreeAfterAllocate(int)} 逻辑相同，
     * 从指定节点的父节点逐层向上更新。
     *
     * @param node 刚被释放的叶子/子树节点
     */
    private void updateTreeAfterFree(int node) {
        int parent = node >>> 1;
        while (parent >= 1) {
            tree[parent] = Math.max(tree[parent << 1], tree[(parent << 1) | 1]);
            parent >>>= 1;
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 创建 ByteBuffer 的切片视图。
     * <p>
     * 通过 position/limit 定位并调用 slice() 创建独立的 ByteBuffer 视图，
     * 切片与原 ByteBuffer 共享底层内存但拥有独立的 position/limit/capacity。
     *
     * @param offset   内存偏移量
     * @param capacity 切片容量
     * @return 新的 ByteBuffer 视图
     */
    private ByteBuffer createSlice(int offset, int capacity) {
        ByteBuffer dup = memory.duplicate();
        dup.position(offset);
        dup.limit(offset + capacity);
        ByteBuffer slice = dup.slice();
        // 设置为填充模式：position=0, limit=capacity
        slice.clear();
        return slice;
    }

    /**
     * 计算 log2(value)。value 必须是 2 的幂。
     *
     * @param value 2 的幂
     * @return log2(value)
     */
    private static int log2(int value) {
        return Integer.numberOfTrailingZeros(value);
    }

    /**
     * 将数值向上取整到最近的 2 的幂。
     * <p>
     * 使用经典的位运算算法（Hacker's Delight）：
     * 先减 1，然后将最高位以下全部填充为 1，最后加 1。
     *
     * @param value 原始值
     * @return >= value 的最小 2 的幂；如果 value <= 0，返回 1
     */
    static int nextPowerOfTwo(int value) {
        if (value <= 0) {
            return 1;
        }
        value--;
        value |= value >>> 1;
        value |= value >>> 2;
        value |= value >>> 4;
        value |= value >>> 8;
        value |= value >>> 16;
        return value + 1;
    }

    // ======================== 访问器 ========================

    /**
     * @return Chunk 管理的总内存容量（字节）
     */
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * @return 最后一次分配的内存偏移量
     */
    public int lastAllocOffset() {
        return lastAllocOffset;
    }

    /**
     * @return 页大小（字节）
     */
    public int pageSize() {
        return pageSize;
    }

    /**
     * @return 是否使用直接内存
     */
    public boolean isDirect() {
        return direct;
    }

    /**
     * @return 已使用的字节数（近似值）
     */
    public int usage() {
        return usage.get();
    }

    /**
     * @return 是否完全空闲（无任何分配）
     */
    public boolean isEmpty() {
        return usage.get() == 0;
    }

    /**
     * 销毁此 Chunk，释放直接内存。
     * 通过反射调用 DirectByteBuffer 的 cleaner 来释放堆外内存。
     */
    void destroy() {
        if (direct) {
            try {
                Method cleanerMethod = memory.getClass().getMethod("cleaner");
                cleanerMethod.setAccessible(true);
                Object cleaner = cleanerMethod.invoke(memory);
                if (cleaner != null) {
                    Method cleanMethod = cleaner.getClass().getMethod("clean");
                    cleanMethod.setAccessible(true);
                    cleanMethod.invoke(cleaner);
                }
            } catch (Exception ignored) {
                // 非 DirectByteBuffer 或反射失败，忽略
            }
        }
    }

    @Override
    public String toString() {
        return String.format("PoolChunk{size=%d,pageSize=%d,maxOrder=%d,direct=%b,usage=%d/%d}",
                chunkSize, pageSize, maxOrder, direct, usage.get(), chunkSize);
    }
}
