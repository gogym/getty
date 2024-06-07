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
import com.gettyio.core.buffer.bytebuf.impl.PooledHeapByteBuf;
import com.gettyio.core.util.StringUtil;

/**
 * 一大块连续的内存空间
 *
 * @param <T>
 */
public abstract class PoolArena<T> {

    /**
     * 计算并设置tiny子页池的数量，每个子页池的大小为512字节。
     * 通过右移操作将512（二进制为10000000000000000）向右移动4位，
     * 相当于除以16，结果为100000（二进制）。
     */
    static final int numTinySubpagePools = 512 >>> 4; //100000

    /**
     * 指向池化缓冲区构造器的引用，用于创建和管理池化缓冲区。
     */
    public final PooledByteBufAllocator parent;

    /**
     * 平衡二叉树的最大深度，用于管理内存块的分配和回收。
     */
    private final int maxOrder;

    /**
     * 每个PoolSubpage的大小，即内存页的大小。
     */
    final int pageSize;

    /**
     * pageSize的2的幂次，用于计算内存地址。
     */
    final int pageShifts;

    /**
     * PoolChunk的总内存大小，即每个内存块的大小。
     */
    final int chunkSize;

    /**
     * 用于判断申请的内存是否超过pageSize的掩码，等于 ~(pageSize-1)。
     * 该掩码用于快速判断内存申请是否需要新的页。
     */
    final int subpageOverflowMask;

    /**
     * 小于pageSize的内存池子页池的数量。
     */
    final int numSmallSubpagePools;

    /**
     * 一组连续的内存空间，tinySubpagePools用于管理小于pageSize的内存块，
     * smallSubpagePools用于管理大于等于pageSize且小于2 * pageSize的内存块。
     */
    private final PoolSubpage<T>[] tinySubpagePools;
    private final PoolSubpage<T>[] smallSubpagePools;

    /**
     * 不同使用频率的内存块列表，根据使用频率将内存块分类管理，
     * 以提高内存的利用率和回收效率。
     */
    private final PoolChunkList<T> q100;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;

    /**
     * 构造函数用于创建一个PoolArena实例。
     *
     * @param parent 指向PooledByteBufAllocator的引用，是这个PoolArena的父对象。
     * @param pageSize 每个内存页的大小。
     * @param maxOrder 平衡二叉树的最大深度，用于内存块的管理。
     * @param pageShifts 内存页大小的2的幂次，用于计算内存偏移。
     * @param chunkSize 每个PoolChunk的大小，即内存块的大小。
     */
    protected PoolArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        // 计算用于判断是否需要新分配内存页的掩码
        subpageOverflowMask = ~(pageSize - 1);

        // 初始化tiny子页池数组
        tinySubpagePools = newSubpagePoolArray(numTinySubpagePools);
        for (int i = 0; i < tinySubpagePools.length; i++) {
            tinySubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        // 计算并初始化small子页池数组
        numSmallSubpagePools = pageShifts - 9;
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);
        for (int i = 0; i < smallSubpagePools.length; i++) {
            smallSubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        // 初始化不同利用率的内存块列表
        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE);
        q075 = new PoolChunkList<T>(this, q100, 75, 100);
        q050 = new PoolChunkList<T>(this, q075, 50, 100);
        q025 = new PoolChunkList<T>(this, q050, 25, 75);
        q000 = new PoolChunkList<T>(this, q025, 1, 50);
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25);

        // 设置内存块列表之间的链接
        q100.prevList = q075;
        q075.prevList = q050;
        q050.prevList = q025;
        q025.prevList = q000;
        q000.prevList = null;
        qInit.prevList = qInit;
    }

    /**
     * 分配一个池化的ByteBuf。
     * <p>
     * 此方法将创建一个新的ByteBuf实例，根据给定的需求容量和最大容量进行初始化，并将其缓存到线程缓存中（如果适用）。
     *
     * @param cache 线程缓存，用于缓存ByteBuf以提高分配效率。
     * @param reqCapacity 请求的容量，表示ByteBuf至少需要的容量。
     * @param maxCapacity 最大容量，表示ByteBuf可以被分配的最大大小。
     * @return 分配并初始化的PooledByteBuf实例。
     */
    PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        // 创建一个新的ByteBuf实例，最大容量为maxCapacity
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        // 为这个ByteBuf实例分配指定的容量，并进行相关初始化
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    /**
     * 重新分配缓冲区的容量。
     * 此方法会根据提供的新容量重新分配给定的PooledByteBuf的内存。如果新容量与旧容量相同，则不执行任何操作。
     * 如果新容量大于旧容量，则会扩容内存并复制旧内存中的数据到新内存中。如果新容量小于旧容量，则会截断内存并调整读写索引。
     * 当freeOldMemory为true时，会释放旧的内存资源。
     *
     * @param buf 需要重新分配容量的PooledByteBuf实例。
     * @param newCapacity 想要的新容量。必须在0到maxCapacity之间，否则会抛出IllegalArgumentException。
     * @param freeOldMemory 当为true时，会释放PooledByteBuf之前分配的内存。
     * @throws IllegalArgumentException 如果newCapacity不在有效范围内。
     */
    public void reallocate(PooledByteBuf<T> buf, int newCapacity, boolean freeOldMemory) {
        // 校验新容量是否合法
        if (newCapacity < 0 || newCapacity > buf.maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int oldCapacity = buf.length; // 获取当前缓冲区的容量
        // 如果新旧容量相同，则无需重新分配
        if (oldCapacity == newCapacity) {
            return;
        }

        // 获取与当前缓冲区关联的旧内存块信息
        PoolChunk<T> oldChunk = buf.chunk;
        long oldHandle = buf.handle;
        T oldMemory = buf.memory;
        int oldOffset = buf.offset;
        int oldMaxLength = buf.maxLength;
        int readerIndex = buf.readerIndex(); // 获取读索引
        int writerIndex = buf.writerIndex(); // 获取写索引

        // 分配新的内存块以适应新的容量
        allocate(parent.threadCache.get(), buf, newCapacity);
        // 扩容场景，将旧数据复制到新内存中
        if (newCapacity > oldCapacity) {
            memoryCopy(
                    oldMemory, oldOffset,
                    buf.memory, buf.offset, oldCapacity);
        // 缩容场景，根据读写索引调整并复制数据
        } else if (newCapacity < oldCapacity) {
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex = newCapacity; // 调整写索引
                }
                memoryCopy(
                        oldMemory, oldOffset + readerIndex,
                        buf.memory, buf.offset + readerIndex, writerIndex - readerIndex);
            } else {
                readerIndex = writerIndex = newCapacity; // 缩容时调整读写索引到新的容量
            }
        }

        buf.setIndex(readerIndex, writerIndex); // 更新缓冲区的读写索引

        // 如果需要，则释放旧的内存资源
        if (freeOldMemory) {
            free(oldChunk, oldHandle, oldMaxLength, buf.initThread == Thread.currentThread());
        }
    }

    public void free(PoolChunk<T> chunk, long handle, int normCapacity, boolean sameThreads) {
        if (chunk.unpooled) {
            destroyChunk(chunk);
        } else {
            if (sameThreads) {
                PoolThreadCache cache = parent.threadCache.get();
                if (cache.add(this, chunk, handle, normCapacity)) {
                    // 缓存所以不释放它。
                    return;
                }
            }

            synchronized (this) {
                chunk.parent.free(chunk, handle);
            }
        }
    }

    /**
     * 查找指定元素大小的子页池头
     * @param elemSize 元素大小，单位为字节
     * @return PoolSubpage<T> 对象，表示找到的子页池头
     */
    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubpage<T>[] table;

        // 判断元素大小是否属于tiny类别
        if (isTiny(elemSize)) {
            // 计算tiny类别元素在池中的索引
            tableIdx = elemSize >>> 4; // 通过右移操作计算索引
            table = tinySubpagePools; // 使用tiny子页池数组
        } else {
            // 处理非tiny类别的元素
            tableIdx = 0; // 初始化索引
            elemSize >>>= 10; // 对元素大小进行右移操作，用于计算在small类别池中的索引
            // 循环计算索引
            while (elemSize != 0) {
                elemSize >>>= 1; // 继续右移
                tableIdx++; // 索引递增
            }
            table = smallSubpagePools; // 使用small子页池数组
        }

        return table[tableIdx]; // 返回找到的子页池头
    }

    static int tinyIdx(int normCapacity) {
        return normCapacity >>> 4;
    }

    static int smallIdx(int normCapacity) {
        int tableIdx = 0;
        int i = normCapacity >>> 10;
        while (i != 0) {
            i >>>= 1;
            tableIdx++;
        }
        return tableIdx;
    }

    /**
     * capacity < pageSize
     */
    boolean isTinyOrSmall(int normCapacity) {
        return (normCapacity & subpageOverflowMask) == 0;
    }

    /**
     * normCapacity < 512
     *
     * @param normCapacity
     * @return
     */
    static boolean isTiny(int normCapacity) {
        return (normCapacity & 0xFFFFFE00) == 0;
    }

    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
        final int normCapacity = normalizeCapacity(reqCapacity);
        // capacity < pageSize
        if (isTinyOrSmall(normCapacity)) {
            int tableIdx;
            PoolSubpage<T>[] table;
            // < 512
            if (isTiny(normCapacity)) {
                if (cache.allocateTiny(buf, reqCapacity, normCapacity)) {
                    // 是否能够从缓存中分配，继续
                    return;
                }
                tableIdx = tinyIdx(normCapacity);
                table = tinySubpagePools;
            } else {
                if (cache.allocateSmall(buf, reqCapacity, normCapacity)) {
                    // 是否能够从缓存中分配，继续
                    return;
                }
                tableIdx = smallIdx(normCapacity);
                table = smallSubpagePools;
            }

            synchronized (this) {
                final PoolSubpage<T> head = table[tableIdx];
                final PoolSubpage<T> s = head.next;
                if (s != head) {
                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubpage(buf, handle, reqCapacity);
                    return;
                }
            }
        } else if (normCapacity <= chunkSize) {
            if (cache.allocateNormal(buf, reqCapacity, normCapacity)) {
                // 是否能够从缓存中分配，继续
                return;
            }
        } else {
            // 巨大的分配从来没有通过缓存服务，所以只要调用allocateHuge
            allocateHuge(buf, reqCapacity);
            return;
        }
        allocateNormal(buf, reqCapacity, normCapacity);
    }

    private synchronized void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
                q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
                q075.allocate(buf, reqCapacity, normCapacity) || q100.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }

        // 添加一个新的块。
        PoolChunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
        long handle = c.allocate(normCapacity);
        assert handle > 0;
        c.initBuf(buf, handle, reqCapacity);
        qInit.add(c);
    }

    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        buf.initUnpooled(newUnpooledChunk(reqCapacity), reqCapacity);
    }

    private int normalizeCapacity(int reqCapacity) {
        if (reqCapacity < 0) {
            throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
        }
        if (reqCapacity >= chunkSize) {
            return reqCapacity;
        }

        // >= 512
        if (!isTiny(reqCapacity)) {
            // 翻了一倍
            int normalizedCapacity = reqCapacity;
            normalizedCapacity--;
            normalizedCapacity |= normalizedCapacity >>> 1;
            normalizedCapacity |= normalizedCapacity >>> 2;
            normalizedCapacity |= normalizedCapacity >>> 4;
            normalizedCapacity |= normalizedCapacity >>> 8;
            normalizedCapacity |= normalizedCapacity >>> 16;
            normalizedCapacity++;

            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }
            return normalizedCapacity;
        }
        // Quantum-spaced
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }

        return (reqCapacity & ~15) + 16;
    }

    private PoolSubpage<T> newSubpagePoolHead(int pageSize) {
        PoolSubpage<T> head = new PoolSubpage<T>(pageSize);
        head.prev = head;
        head.next = head;
        return head;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpagePoolArray(int size) {
        return new PoolSubpage[size];
    }


    protected abstract PoolChunk<T> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);

    protected abstract PoolChunk<T> newUnpooledChunk(int capacity);

    protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);

    protected abstract void memoryCopy(T src, int srcOffset, T dst, int dstOffset, int length);

    protected abstract void destroyChunk(PoolChunk<T> chunk);

    public synchronized String toString() {
        StringBuilder buf = new StringBuilder()
                .append("Chunk(s) at 0~25%:")
                .append(StringUtil.NEWLINE)
                .append(qInit)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 0~50%:")
                .append(StringUtil.NEWLINE)
                .append(q000)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 25~75%:")
                .append(StringUtil.NEWLINE)
                .append(q025)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 50~100%:")
                .append(StringUtil.NEWLINE)
                .append(q050)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 75~100%:")
                .append(StringUtil.NEWLINE)
                .append(q075)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 100%:")
                .append(StringUtil.NEWLINE)
                .append(q100)
                .append(StringUtil.NEWLINE)
                .append("tiny subpages:");
        for (int i = 1; i < tinySubpagePools.length; i++) {
            PoolSubpage<T> head = tinySubpagePools[i];
            if (head.next == head) {
                continue;
            }

            buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ");
            PoolSubpage<T> s = head.next;
            for (; ; ) {
                buf.append(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
        buf.append(StringUtil.NEWLINE)
                .append("small subpages:");
        for (int i = 1; i < smallSubpagePools.length; i++) {
            PoolSubpage<T> head = smallSubpagePools[i];
            if (head.next == head) {
                continue;
            }

            buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ");
            PoolSubpage<T> s = head.next;
            for (; ; ) {
                buf.append(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
        buf.append(StringUtil.NEWLINE);

        return buf.toString();
    }

    public static final class HeapArena extends PoolArena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new PoolChunk<byte[]>(this, new byte[chunkSize], pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected PoolChunk<byte[]> newUnpooledChunk(int capacity) {
            return new PoolChunk<byte[]>(this, new byte[capacity], capacity);
        }

        @Override
        protected void destroyChunk(PoolChunk<byte[]> chunk) {
            // 依靠GC。
        }

        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return PooledHeapByteBuf.newInstance(maxCapacity);
        }

        @Override
        protected void memoryCopy(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
            if (length == 0) {
                return;
            }

            System.arraycopy(src, srcOffset, dst, dstOffset, length);
        }
    }

}
