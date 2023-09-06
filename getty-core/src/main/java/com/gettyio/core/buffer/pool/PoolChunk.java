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
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 * <p>
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 * <p>
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 * <p>
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 * <p>
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 * <p>
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 * <p>
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 * <p>
 * depth=maxOrder is the last level and the leafs consist of pages
 * <p>
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 * <p>
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 * memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 * is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 * <p>
 * As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 * <p>
 * Initialization -
 * In the beginning we construct the memoryMap array by storing the depth of a node at each node
 * i.e., memoryMap[id] = depth_of_id
 * <p>
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 * some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 * is thus marked as unusable
 * <p>
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 * <p>
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 * <p>
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 * note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 * <p>
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information (i.e, 2 byte vals) as a short value in memoryMap
 * <p>
 * memoryMap[id]= (depth_of_id, x)
 * where as per convention defined above
 * the second value (i.e, x) indicates that the first node which is free to be allocated is at depth x (from root)
 */

import com.gettyio.core.buffer.bytebuf.impl.PooledByteBuf;

/**
 * PoolArena 包含一系列的PoolChunk
 *
 * @param <T>
 */
public final class PoolChunk<T> {
    /**
     * 当前内存块所属的内存空间
     */
    public final PoolArena<T> arena;
    /**
     * 待分配的内存，如果是堆内存那么这里就是byte[]，如果是直接内存那么就是一个java.nioDirectByteBuffer实例
     */
    public final T memory;
    /**
     * 是否是可重用的，unpooled=false表示可重用
     */
    public final boolean unpooled;
    /**
     * PoolChunk的物理视图是连续的PoolSubpage,用PoolSubpage保持，而memoryMap是所有PoolSubpage的
     * 逻辑映射，映射为一颗平衡二叉数，用来标记每一个PoolSubpage是否被分配
     */
    private final byte[] memoryMap;
    /**
     * 存储每个节点在二叉树的深度
     */
    private final byte[] depthMap;
    /**
     * 该PoolChunk所包含的PoolSupage。也就是PoolChunk连续的可用内存。
     */
    private final PoolSubpage<T>[] subpages;
    /**
     * 用来判断申请的内存是否超过pageSize的掩码，等于  ~(pageSize-1)
     */
    private final int subpageOverflowMask;

    /**
     * 每个PoolSubpage的大小，默认为8192个字节（8K)
     */
    private final int pageSize;
    /**
     * pageSize 2的 pageShifts幂
     */
    private final int pageShifts;
    /**
     * 平衡二叉树的深度，一个PoolChunk包含 2的 maxOrder幂(  1 << maxOrder ) 个PoolSubpage。
     */
    private final int maxOrder;
    /**
     * PoolChunk的总内存大小,chunkSize =   (1<<maxOrder) * pageSize。
     */
    private final int chunkSize;
    /**
     * chunkSize 是  2的 log2ChunkSize幂，如果chunkSize = 2 的 10次幂,那么 log2ChunkSize=10
     */
    private final int log2ChunkSize;
    /**
     * PoolChunk由maxSubpageAllocs个PoolSubpage组成。
     */
    private final int maxSubpageAllocs;
    /**
     * 标记为已被分配的值，该值为 maxOrder + 1
     */
    private final byte unusable;
    /**
     * 当前PoolChunk空闲的内存。
     */
    private int freeBytes;
    /**
     * 一个PoolChunk分配后，会根据使用率挂在一个PoolChunkList中，在(PoolArena的PoolChunkList上)
     */
    PoolChunkList<T> parent;
    /**
     * 前后相连的PoolChunk，PoolChunk本身设计为一个链表结构
     */
    PoolChunk<T> prev;
    PoolChunk<T> next;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize-1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // 生成内存映射。
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++d) { // 每次移动一层树
            int depth = 1 << d;
            for (int p = 0; p < depth; ++p) {
                // 在每一层中从左到右遍历，并设置值为子树的深度
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs);
    }

    /**
     * 创建一个未被池化的特殊块。
     */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
    }

    /**
     * 创建内存子页数组
     *
     * @param size
     * @return
     */
    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    /**
     * 计算当前块使用率
     *
     * @return
     */
    int usage() {
        final int freeBytes = this.freeBytes;
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    long allocate(int normCapacity) {
        //(normCapacity & subpageOverflowMask) 可以理解为判断两个变量不为0
        if ((normCapacity & subpageOverflowMask) != 0) {
            // >= pageSize
            return allocateRun(normCapacity);
        } else {
            return allocateSubpage(normCapacity);
        }
    }

    /**
     * 由allocate使用的更新方法
     * 只有在分配了一个继承节点和它的所有前任节点时才会触发此操作
     * 需要更新他们的状态
     * 以id为根的子树有空闲空间的最小深度
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    /**
     * 免费使用的更新方法
     * 这需要处理两个孩子都完全自由的特殊情况
     * 在这种情况下，根据size = child-size * 2的请求直接分配parent
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            logChild -= 1; // 在第一次迭代中等于log，然后在向上遍历时从logChild中减少1

            if (val1 == logChild && val2 == logChild) {
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * 当查询深度为d的空闲节点时，在memoryMap中分配索引的算法
     *
     * @param d depth
     * @return index in memoryMap
     */
    private int allocateNode(int d) {
        int id = 1;
        int initial = -(1 << d); // has last d bits = 0 and rest all = 1
        byte val = value(id);
        if (val > d) { // unusable
            return -1;
        }
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;
            val = value(id);
            if (val > d) {
                id ^= 1;
                val = value(id);
            }
        }
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        setValue(id, unusable); // 标记为不可用
        updateParentsAlloc(id);
        return id;
    }

    /**
     * Allocate a run of pages (>=1)
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {
        int d = maxOrder - (log2(normCapacity) - pageShifts);
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    /**
     * 创建/初始化一个新的normCapacity的PoolSubpage
     * 这里创建/初始化的任何PoolSubpage都被添加到拥有这个PoolChunk的PoolArena的子页面池中
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateSubpage(int normCapacity) {
        int d = maxOrder; // subpages are only be allocated from pages i.e., leaves
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }

        final PoolSubpage<T>[] subpages = this.subpages;
        final int pageSize = this.pageSize;

        freeBytes -= pageSize;

        int subpageIdx = subpageIdx(id);
        PoolSubpage<T> subpage = subpages[subpageIdx];
        if (subpage == null) {
            subpage = new PoolSubpage<T>(this, id, runOffset(id), pageSize, normCapacity);
            subpages[subpageIdx] = subpage;
        } else {
            subpage.init(normCapacity);
        }
        return subpage.allocate();
    }

    /**
     * 释放一个子页面或一组页面
     * 当从PoolSubpage释放子页面时，它可能被添加回所属PoolArena的子页面池
     * 如果PoolArena中的子页面池至少有一个给定元素大小的其他PoolSubpage，则可以
     * 完全释放拥有的页面，以便它可以用于后续的分配
     *
     * @param handle handle to free
     */
    void free(long handle) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> Integer.SIZE);

        if (bitmapIdx != 0) { // free a subpage
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;
            if (subpage.free(bitmapIdx & 0x3FFFFFFF)) {
                return;
            }
        }
        freeBytes += runLength(memoryMapIdx);
        setValue(memoryMapIdx, depth(memoryMapIdx));
        updateParentsFree(memoryMapIdx);
    }

    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> Integer.SIZE);
        if (bitmapIdx == 0) {
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx));
        } else {
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, (int) (handle >>> Integer.SIZE), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = (int) handle;

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
                this, handle,
                runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, reqCapacity, subpage.elemSize);
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(val);
    }

    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }

    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);
    }

    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

}
