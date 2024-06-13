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

    /**
     * PoolChunk类的构造函数，用于初始化一个池块。
     * 池块是对象池管理内存的的基本单位，包含了一块内存区域及其管理信息。
     *
     * @param arena 池块所属的Arena，用于管理多个池块。
     * @param memory 池块对应的内存区域。
     * @param pageSize 每页的大小，影响池块的内存分配策略。
     * @param maxOrder 页大小的最大指数，用于计算最大分配单元。
     * @param pageShifts 页大小的位移量，用于快速计算页相关的内存偏移。
     * @param chunkSize 池块的总大小，即可用于分配的内存大小。
     */
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
     * 构造函数初始化PoolChunk对象。
     * 该构造函数用于创建一个未分配的池块，主要用于管理内存块的分配和释放。
     *
     * @param arena 池块所属的竞技场，用于管理多个池块。
     * @param memory 与池块关联的内存块。
     * @param size 池块的大小。
     */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        // 标记此池块为未分配，即不在池中管理的内存。
        unpooled = true;
        // 设置池块所属的竞技场。
        this.arena = arena;
        // 设置与池块关联的内存块。
        this.memory = memory;
        // 未使用的内存映射表，对于未分配的池块来说不需要。
        memoryMap = null;
        // 未使用的深度映射表，对于未分配的池块来说不需要。
        depthMap = null;
        // 未使用的子页数组，对于未分配的池块来说不需要。
        subpages = null;
        // 子页溢出掩码初始化为0，对于未分配的池块来说没有意义。
        subpageOverflowMask = 0;
        // 页大小初始化为0，对于未分配的池块来说没有意义。
        pageSize = 0;
        // 页位移初始化为0，对于未分配的池块来说没有意义。
        pageShifts = 0;
        // 最大订单初始化为0，对于未分配的池块来说没有意义。
        maxOrder = 0;
        // 无法使用的标记设置为(maxOrder + 1)，表示此池块不可用。
        unusable = (byte) (maxOrder + 1);
        // 设置池块的大小。
        chunkSize = size;
        // 计算池块大小的对数，用于快速计算。
        log2ChunkSize = log2(chunkSize);
        // 最大子页分配数初始化为0，对于未分配的池块来说没有意义。
        maxSubpageAllocs = 0;
    }


    /**
     * 根据给定的大小创建一个PoolSubpage数组。
     * 该方法用于动态地分配PoolSubpage数组，以适应对象池管理的需要。
     * 由于Java的类型擦除，这里使用了@SuppressWarnings注解来抑制编译器警告。
     *
     * @param size 数组的大小。指定数组将包含的PoolSubpage元素的数量。
     * @return 新创建的PoolSubpage数组。这个数组将被用来存储和管理对象池中的对象。
     */
    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }



    /**
     * 计算存储空间的使用率。
     * 使用率是根据当前可用的字节数与chunkSize的比值来计算的。如果没有任何可用空间（freeBytes为0），
     * 则使用率视为100%。如果可用空间占总空间的百分比非常小（即freePercentage为0），为了防止计算结果为0，
     * 使用率被视为99%。这种处理方式是为了避免因分母过小导致的精度问题。
     *
     * @return 存储空间的使用率，以百分比形式表示。
     */
    int usage() {
        // 获取当前可用的字节数
        final int freeBytes = this.freeBytes;

        // 如果没有可用空间，返回100，表示使用率为100%
        if (freeBytes == 0) {
            return 100;
        }

        // 计算可用空间占总空间的百分比
        int freePercentage = (int) (freeBytes * 100L / chunkSize);

        // 如果可用空间百分比为0，返回99，避免计算结果为0
        if (freePercentage == 0) {
            return 99;
        }

        // 返回使用率，即100减去可用空间的百分比
        return 100 - freePercentage;
    }


    /**
     * 根据给定的容量分配内存。
     * 此方法负责决定是分配一个子页还是一个运行（取决于容量是否超过子页大小）。
     *
     * @param normCapacity 请求的容量，已规范化，即小于或等于最大容量。
     * @return 分配的内存块的起始地址。
     */
    long allocate(int normCapacity) {
        /*
         * 检查请求的容量是否超过子页的容量限制。
         * 如果请求的容量超过子页容量，将通过allocateRun方法分配内存，
         * 否则通过allocateSubpage方法分配内存。
         */
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

    /**
     * 初始化缓冲区。
     * 根据提供的handle分解出memoryMapIdx和bitmapIdx，用于不同情况下的缓冲区初始化。
     * 如果bitmapIdx为0，表示数据位于page内，直接初始化缓冲区；
     * 否则，表示数据位于subpage内，需进一步处理。
     *
     * @param buf       要初始化的缓冲区对象
     * @param handle    给定的handle，包含memoryMapIdx和bitmapIdx信息
     * @param reqCapacity 请求的容量，用于初始化缓冲区
     */
    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        // 提取handle中的memoryMapIdx
        int memoryMapIdx = (int) handle;
        // 提取handle中的bitmapIdx
        int bitmapIdx = (int) (handle >>> Integer.SIZE);

        // 当bitmapIdx为0时，表示数据位于page内
        if (bitmapIdx == 0) {
            // 验证memoryMapIdx对应的位置是否未被使用
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            // 使用相关信息初始化缓冲区
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx));
        } else {
            // 当bitmapIdx不为0时，表示数据位于subpage内，调用相应方法处理
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }


    /**
     * 使用子页面初始化缓冲区。
     *
     * 此方法通过使用给定的句柄和请求的容量来初始化缓冲区，具体实现是通过调用另一个重载的initBufWithSubpage方法来完成。
     * 它的主要作用是处理大容量数据的存储，通过将数据分割成更小的子页面，以便更有效地管理和访问这些数据。
     *
     * @param buf 待初始化的缓冲区对象，这是一个池化的字节缓冲区。
     * @param handle 子页面的句柄，用于标识和访问特定的子页面。
     * @param reqCapacity 请求的容量，即初始化缓冲区时希望的容量大小。
     */
    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        // 调用重载的initBufWithSubpage方法，实际初始化缓冲区。
        initBufWithSubpage(buf, handle, (int) (handle >>> Integer.SIZE), reqCapacity);
    }

    /**
     * 使用子页面初始化缓冲区。
     * 此方法用于将一个PooledByteBuf与池化的子页面相关联，子页面是从一个更大的内存页中划分出来的。
     * 它通过给定的句柄和位图索引来定位具体的子页面，并初始化缓冲区，以便它可以从子页面中分配内存。
     *
     * @param buf 要初始化的缓冲区对象。
     * @param handle 子页面的句柄，用于在内存映射中定位子页面。
     * @param bitmapIdx 位图索引，用于在子页面中定位具体的元素。
     * @param reqCapacity 请求的容量，即初始化缓冲区时希望分配的字节数。
     */
    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        // 确保位图索引不为0，因为0表示空闲状态。
        assert bitmapIdx != 0;

        // 提取句柄中的内存映射索引。
        int memoryMapIdx = (int) handle;

        // 根据内存映射索引获取对应的子页面。
        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        // 确保该子页面标记为不可销毁，以防止在初始化过程中被错误地释放。
        assert subpage.doNotDestroy;
        // 确保请求的容量不超过子页面单个元素的大小。
        assert reqCapacity <= subpage.elemSize;

        // 初始化缓冲区，设置其基础属性，包括所属池、句柄、实际内存地址、请求容量和元素大小。
        buf.init(
                this, handle,
                runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, reqCapacity, subpage.elemSize);
    }


    /**
     * 根据给定的ID从内存映射中获取对应的字节值。
     * 此方法假设内存映射（memoryMap）是一个已经初始化并存储了相关数据的数据结构。
     *
     * @param id 内存映射中的元素索引，用于定位具体的字节数据。
     * @return 返回位于内存映射中指定ID位置的字节值。
     */
    private byte value(int id) {
        return memoryMap[id];
    }


    /**
     * 设置指定内存位置的值。
     *
     * 此方法用于更新内存映射表中特定位置的值。内存映射表是一个数组，通过索引（id）可以访问和修改其元素。
     * 使用byte类型作为值，可以确保对内存的低级别操作具有更高的灵活性和效率。
     *
     * @param id 内存位置的索引。此索引用于在内存映射表中定位要修改的内存位置。
     * @param val 要设置的新值。这是一个byte类型的值，将被存储在指定的内存位置。
     */
    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }


    /**
     * 获取给定ID对应的深度值。
     *
     * 此方法通过查询depthMap数组来获取特定ID的深度值。depthMap是一个映射数组，
     * 其中每个ID都与其相应的深度值对应。使用此方法可以快速检索到特定ID的深度信息，
     * 而不需要进行复杂的计算或搜索。
     *
     * @param id 需要查询深度的ID。
     * @return 与给定ID对应的深度值。
     */
    private byte depth(int id) {
        return depthMap[id];
    }


    /**
     * 计算给定整数的二进制表示中最高位的位置。
     * 该方法通过计算val的二进制表示中最高位的位置来实现，这等同于计算val的二进制表示中最高位的索引。
     * 由于Java中的整数是用补码表示的，最高位是符号位，因此对于正数，结果是其二进制表示中最高位的位置，
     * 对于负数，结果是其二进制表示中最高位的位置减去1（因为负数的最高位被用作符号位）。
     *
     * @param val 待计算的整数，可以是正数或负数。
     * @return 整数val的二进制表示中最高位的位置索引。
     */
    private static int log2(int val) {
        // 使用Integer.SIZE获取int类型的二进制位数，然后减去val的最高位前面的0的个数。
        // 这样做的结果是得到val的最高位的索引，对于负数，由于符号位的原因，需要再减去1。
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(val);
    }


    /**
     * 计算给定ID的运行长度。
     * 运行长度是基于ID在分块结构中的深度计算得出的，用于确定一个ID所代表的值在编码后的连续长度。
     * 此函数通过利用块大小的对数和ID的深度来计算这个长度，体现了数据结构的特性和编码策略。
     *
     * @param id 待计算运行长度的ID。
     * @return 返回计算得到的运行长度。
     */
    private int runLength(int id) {
        // 使用位移操作符将1左移log2ChunkSize - depth(id)位，结果即为ID的运行长度。
        // 这里利用了位运算的高效性和对数运算的关系，巧妙地计算了运行长度，体现了算法的精妙之处。
        return 1 << log2ChunkSize - depth(id);
    }


    /**
     * 计算给定id对应的偏移量。
     * 该方法通过位运算和深度计算，结合运行长度，来确定特定id在序列中的偏移量。
     * 这对于理解序列中元素的排列和分布具有重要意义。
     *
     * @param id 待计算偏移量的id。
     * @return 返回计算得到的偏移量。
     */
    private int runOffset(int id) {
        // 使用异或运算和左移操作计算shift值，用于确定偏移量。
        // 异或运算保证了对称性，左移操作的位数由深度决定，这影响了偏移量的大小。
        int shift = id ^ 1 << depth(id);
        // 将shift值乘以运行长度，得到最终的偏移量。
        // 这里运行长度的作用是进一步调整偏移量，确保其符合序列的特定排列规律。
        return shift * runLength(id);
    }


    /**
     * 计算子页面索引。
     *
     * 该方法通过位运算来计算给定内存映射索引所对应的子页面索引。子页面索引的计算是基于内存映射索引和最大子页面分配数量的异或操作。
     * 这种计算方式旨在快速定位到特定的子页面，而无需通过复杂的计算或查找过程。
     *
     * @param memoryMapIdx 内存映射索引，用于确定子页面的位置。
     * @return 子页面索引，作为内存映射索引的某种转换结果。
     */
    private int subpageIdx(int memoryMapIdx) {
        // 使用异或操作符来计算子页面索引，异或操作可以快速地在不改变原始数据的基础上进行位运算。
        return memoryMapIdx ^ maxSubpageAllocs;
    }


}
