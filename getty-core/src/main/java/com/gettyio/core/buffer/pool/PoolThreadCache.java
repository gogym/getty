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

    /**
     * 用于堆内存分配的池区。
     */
    final PoolArena<byte[]> heapArena;

    /**
     * 用于存储极小尺寸堆内存块的缓存数组。
     * 这些缓存用于快速分配和回收极小尺寸的堆内存块。
     */
    private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;
    /**
     * 用于存储小尺寸堆内存块的缓存数组。
     * 这些缓存用于快速分配和回收小尺寸的堆内存块。
     */
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    /**
     * 用于存储正常尺寸堆内存块的缓存数组。
     * 这些缓存用于快速分配和回收正常尺寸的堆内存块。
     */
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;

    /**
     * 用于正常尺寸堆内存分配的位移数量。
     * 这个值用于计算内存分配时的对齐参数。
     */
    private final int numShiftsNormalHeap;
    /**
     * 自由扫描分配阈值。
     * 当堆内存的空闲块超过这个阈值时，会触发自由扫描来回收内存。
     */
    private final int freeSweepAllocationThreshold;

    /**
     * 当前的内存分配计数。
     * 用于追踪和管理内存分配的总量。
     */
    private int allocations;

    /**
     * 与这个分配器关联的线程。
     * 这个字段用于确保内存分配器主要用于单线程环境下，
     * 以提高内存分配的效率和缓存的命中率。
     */
    private final Thread thread = Thread.currentThread();


    /**
     * 定义一个常量Runnable对象，用于执行特定的无限制任务。
     * 这种做法使得任务的定义只出现一次，而在需要执行任务的地方，可以直接使用这个对象。
     * 任务的具体内容在run方法中定义，这里通过调用free0方法来实现任务的逻辑。
     */
    private final Runnable freeTask = new Runnable() {
        @Override
        public void run() {
            free0();
        }
    };


    /**
     * 创建一个堆内存缓存池，用于管理不同大小的缓存块。
     *
     * @param heapArena 提供堆内存页的Arena。
     * @param tinyCacheSize 小缓存区的大小，用于存储非常小的缓冲区。
     * @param smallCacheSize 小缓存区的大小，用于存储相对较小的缓冲区。
     * @param normalCacheSize 正常大小缓存区的大小，用于存储中等大小的缓冲区。
     * @param maxCachedBufferCapacity 缓存中最大的缓冲区容量。
     * @param freeSweepAllocationThreshold 自由扫描分配的阈值。
     * @throws IllegalArgumentException 如果maxCachedBufferCapacity或freeSweepAllocationThreshold的值不合法。
     */
    PoolThreadCache(PoolArena<byte[]> heapArena,
                        int tinyCacheSize, int smallCacheSize, int normalCacheSize,
                        int maxCachedBufferCapacity, int freeSweepAllocationThreshold) {
        // 验证maxCachedBufferCapacity的值是否合法
        if (maxCachedBufferCapacity < 0) {
            throw new IllegalArgumentException("maxCachedBufferCapacity: "
                    + maxCachedBufferCapacity + " (expected: >= 0)");
        }
        // 验证freeSweepAllocationThreshold的值是否合法
        if (freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                    + maxCachedBufferCapacity + " (expected: > 0)");
        }
        // 初始化自由扫描分配的阈值
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        // 初始化堆Arena
        this.heapArena = heapArena;

        // 根据heapArena的存在与否初始化不同大小的缓存
        if (heapArena != null) {
            // 初始化非常小的缓存区
            // 为堆分配创建缓存
            tinySubPageHeapCaches = createSubPageCaches(tinyCacheSize, PoolArena.numTinySubpagePools);
            // 初始化较小的缓存区
            smallSubPageHeapCaches = createSubPageCaches(smallCacheSize, heapArena.numSmallSubpagePools);

            // 计算正常大小缓存区所需的位移数
            numShiftsNormalHeap = log2(heapArena.pageSize);
            // 初始化正常大小的缓存区
            normalHeapCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, heapArena);
        } else {
            // 如果没有heapArena，则不初始化任何缓存
            // 没有配置heapArea，所以清空所有缓存
            tinySubPageHeapCaches = null;
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
            numShiftsNormalHeap = -1;
        }

        // 注册线程死亡监听器，确保线程退出时清理缓存
        // 线程本地缓存将保存一个缓冲池列表，当线程不再活跃时，这些缓冲池必须返回到池中。
        ThreadDeathWatcher.watch(thread, freeTask);
    }

    /**
     * 根据指定的缓存大小和缓存数量创建子页面内存区域缓存数组。
     * <p>
     * 此方法用于初始化一个缓存数组，每个缓存对象的大小由cacheSize指定。
     * 如果cacheSize大于0，则创建指定数量的缓存对象；否则，返回null。
     *
     * @param cacheSize 每个缓存对象的大小。决定了每个子页面内存区域缓存能够存储的数据量。
     * @param numCaches 缓存数组的长度。决定了将创建多少个子页面内存区域缓存。
     * @param <T> 缓存对象的泛型类型，支持多种类型的缓存。
     * @return 创建的子页面内存区域缓存数组，如果cacheSize不大于0，则返回null。
     */
    private static <T> SubPageMemoryRegionCache<T>[] createSubPageCaches(int cacheSize, int numCaches) {
        // 判断缓存大小是否大于0，以决定是否创建缓存数组
        if (cacheSize > 0) {
            // 使用 SuppressWarnings 注解来忽略泛型转换的警告
            // 这是因为在这里需要将泛型数组强制转换为 SubPageMemoryRegionCache<T>[]
            @SuppressWarnings("unchecked")
            SubPageMemoryRegionCache<T>[] cache = new SubPageMemoryRegionCache[numCaches];

            // 遍历数组，为每个元素创建一个新的 SubPageMemoryRegionCache 实例
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize);
            }
            // 返回创建好的缓存数组
            return cache;
        } else {
            // 如果缓存大小不大于0，返回null表示不创建缓存数组
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

    /**
     * 计算一个整数的二进制表示中最高位1的位置。
     * 该函数实际计算的是val的二进制形式的最高位1的位置，这等价于计算log2(val)的整数部分。
     * 例如，当val等于8时，二进制表示为1000，最高位1的位置是3，因此函数返回3。
     *
     * @param val 需要计算的整数，必须大于1。
     * @return 返回val的二进制表示中最高位1的位置。
     */
    private static int log2(int val) {
        /* 初始化结果变量为0，表示目前没有找到最高位的1。 */
        int res = 0;
        /* 当val大于1时，循环继续。 */
        while (val > 1) {
            /* 将val向右移一位，相当于除以2，查看下一位是否是1。 */
            val >>= 1;
            /* 如果是1，则结果res加1，表示找到了一个更高的位。 */
            res++;
        }
        /* 返回计算出的最高位1的位置。 */
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
     * 内存区域缓存的抽象静态类，用于缓存特定类型的内存区域。
     * 该类为抽象类，不允许直接实例化，旨在被子类继承。
     *
     * @param <T> 缓存项的类型。
     */
    private abstract static class MemoryRegionCache<T> {
        /**
         * 缓存条目的数组，用于存储缓存的内存区域。
         */
        private final Entry<T>[] entries;

        /**
         * 最大未使用的缓存数量，超过该数量时，会清除部分未使用的缓存。
         */
        private final int maxUnusedCached;

        /**
         * 缓存环形数组的头部索引，指向当前最旧的缓存条目。
         */
        private int head;

        /**
         * 缓存环形数组的尾部索引，指向当前最新的缓存条目。
         */
        private int tail;

        /**
         * 缓存中最大条目的使用数量，用于控制缓存回收的时机。
         */
        private int maxEntriesInUse;

        /**
         * 当前正在使用的缓存条目数量。
         */
        private int entriesInUse;


        @SuppressWarnings("unchecked")
        /**
         * MemoryRegionCache的构造函数。
         * 初始化缓存区域，为提高效率，缓存的大小被设置为2的幂次方。
         * 同时，最大未使用的缓存区域被设定为缓存大小的一半，以平衡缓存利用率和内存消耗。
         *
         * @param size 缓存区域的大小，该大小将被调整为最接近的2的幂次方。
         */
        MemoryRegionCache(int size) {
            // 根据给定的size计算出最接近的2的幂次方大小，用于初始化entries数组。
            entries = new Entry[powerOfTwo(size)];
            // 初始化entries数组中的每个元素为一个新的Entry实例。
            // 这确保了缓存一开始就是完全填充的，可以立即用于缓存操作。
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new Entry<T>();
            }
            // 设置最大未使用的缓存区域数量，这个值是size的一半。
            // 这个阈值用于决定何时应该清除缓存以回收内存。
            maxUnusedCached = size / 2;
        }

        /**
         * 计算最接近给定数的最小2的幂次方数。
         * 该方法通过位操作来实现，而非传统的循环或递归方式，以提高计算效率。
         *
         * @param res 输入的整数，函数将返回不小于该数的最小2的幂次方数。
         * @return 不小于输入数的最小2的幂次方数。
         */
        private static int powerOfTwo(int res) {
            // 如果输入数小于等于2，直接返回2，因为2以下是2的幂次方的最小值。
            if (res <= 2) {
                return 2;
            }
            // 先将res减1，以便后续操作可以正确找到大于res的最小2的幂次方数。
            res--;
            // 通过位操作计算不小于res的最小2的幂次方数。
            // 以下操作依次处理1个、2个、4个、8个、16个二进制位，确保所有位都设置为1。
            res |= res >> 1; // 处理2的1次幂
            res |= res >> 2; // 处理2的2次幂
            res |= res >> 4; // 处理2的4次幂
            res |= res >> 8; // 处理2的8次幂
            res |= res >> 16; // 处理2的16次幂
            // 最后加1，得到不小于res的最小2的幂次方数。
            res++;
            // 返回计算结果。
            return res;
        }

        /**
         * Init the {@link PooledByteBuf} using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract void initBuf(PoolChunk<T> chunk, long handle,
                                        PooledByteBuf<T> buf, int reqCapacity);


        /**
         * 将一个池块和其对应的句柄添加到池的尾部。
         * 此方法用于管理池中的块，确保每个块只被添加一次。如果池的尾部已经有块了，
         * 则说明池已满，无法再添加新块。在这种情况下，方法将返回false。
         *
         * @param chunk 要添加到池中的池块。
         * @param handle 与池块对应的句柄，用于标识和管理池块。
         * @return 如果成功添加了池块，则返回true；如果池已满，添加失败，则返回false。
         */
        public boolean add(PoolChunk<T> chunk, long handle) {
            // 获取当前池尾部的条目。
            Entry<T> entry = entries[tail];

            // 检查当前条目是否已被占用，如果已被占用，则返回false，表示添加失败。
            if (entry.chunk != null) {
                return false;
            }

            // 减少当前正在使用的条目数，因为我们将添加一个新的池块。
            entriesInUse--;

            // 将池块和句柄分配给当前条目。
            entry.chunk = chunk;
            entry.handle = handle;

            // 更新池的尾部索引，以便下一次添加操作可以继续。
            tail = nextIdx(tail);

            return true; // 添加成功，返回true。
        }



        /**
         * 尝试分配一个字节缓冲区。
         * <p>
         * 此方法尝试从池中分配一个字节缓冲区，用于满足给定容量需求。它首先检查当前头部条目的状态，
         * 如果条目可用，则初始化缓冲区并更新池的状态；如果不可用，则分配失败。
         *
         * @param buf         池化的字节缓冲区，将被初始化并使用。
         * @param reqCapacity 需求的缓冲区容量。
         * @return 如果分配成功，则返回true；否则返回false。
         */
        public boolean allocate(PooledByteBuf<T> buf, int reqCapacity) {
            // 获取当前头部条目。
            Entry<T> entry = entries[head];

            // 检查头部条目的chunk是否为空，如果为空，则分配失败。
            if (entry.chunk == null) {
                return false;
            }

            // 标记当前条目正在使用中。
            entriesInUse++;
            // 更新最大使用条目数。
            if (maxEntriesInUse < entriesInUse) {
                maxEntriesInUse = entriesInUse;
            }
            // 初始化缓冲区，准备使用。
            initBuf(entry.chunk, entry.handle, buf, reqCapacity);

            // 重置条目，为下一次分配做准备。
            entry.chunk = null;
            // 移动头部索引，指向下一个可用条目。
            head = nextIdx(head);
            return true;
        }


        /**
         * 释放缓存条目的方法。
         * 该方法遍历缓存中的所有条目，尝试释放它们。释放条目的条件由freeEntry方法确定。
         * 方法会持续释放条目直到遍历完所有条目或遇到无法释放的条目为止。
         *
         * @return 释放的条目数量。
         */
        public int free() {
            // 初始化已释放条目的数量
            int numFreed = 0;
            // 重置当前使用中的条目数量和历史最大使用条目数量
            entriesInUse = 0;
            maxEntriesInUse = 0;
            // 从头部开始遍历缓存条目
            for (int i = head; ; i = nextIdx(i)) {
                // 尝试释放当前条目，如果成功，则增加释放的条目数量
                if (freeEntry(entries[i])) {
                    numFreed++;
                } else {
                    // 如果无法释放当前条目，则结束遍历并返回已释放的条目数量
                    return numFreed;
                }
            }
        }


        /**
         * 精简缓存，释放一部分未使用的条目。
         * 该方法的目的是为了在缓存大小超过预设的最大使用条目数时，释放一部分条目以供后续使用。
         * 它首先计算出需要释放的条目数量，然后遍历缓存条目，释放符合条件的条目。
         *
         * @see #free Entry(int)
         * @see #nextIdx(int)
         */
        private void trim() {
            // 计算需要释放的条目数量
            int free = size() - maxEntriesInUse;
            // 重置已使用和最大使用条目的计数
            entriesInUse = 0;
            maxEntriesInUse = 0;

            // 如果需要释放的条目数量不超过最大未使用缓存的限制，则无需进行精简
            if (free <= maxUnusedCached) {
                return;
            }

            // 从头部开始遍历缓存条目
            int i = head;
            for (; free > 0; free--) {
                // 尝试释放条目，如果释放失败，则跳出循环
                if (!freeEntry(entries[i])) {
                    // all freed
                    break;
                }
                // 移动到下一个条目
                i = nextIdx(i);
            }

            // 更新缓存的头部位置
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
