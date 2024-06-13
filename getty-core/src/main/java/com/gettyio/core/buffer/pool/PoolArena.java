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
 * PoolArena是池化对象的管理区域，负责对象的分配和回收。
 * 它是一个抽象类，具体实现针对不同的内存分配策略和对象池化需求进行。
 *
 * @param <T> 池化对象的类型。
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

    /**
     * 释放池块。
     *
     * 此方法用于将一个池块返回到池中，或者在某些条件下销毁它。它首先检查池块是否标记为未池化，
     * 如果是，则直接销毁该池块。如果池块是池化的，并且释放操作是在相同的线程中进行的，
     * 则尝试将池块添加到线程缓存中。如果添加失败，则同步地将池块返回给其父池。
     *
     * @param chunk 要释放的池块。
     * @param handle 池块的句柄，用于标识池块。
     * @param normCapacity 池块的规范容量，即池块能够提供的最大容量。
     * @param sameThreads 标志位，指示是否在相同的线程中释放池块。
     */
    public void free(PoolChunk<T> chunk, long handle, int normCapacity, boolean sameThreads) {
        // 检查池块是否标记为未池化，如果是，则直接销毁
        if (chunk.unpooled) {
            destroyChunk(chunk);
        } else {
            // 如果在同一线程中释放，并且缓存可用，尝试将池块添加到线程缓存中
            if (sameThreads) {
                PoolThreadCache cache = parent.threadCache.get();
                if (cache.add(this, chunk, handle, normCapacity)) {
                    // 如果添加成功，则不需要进一步处理，直接返回
                    return;
                }
            }

            // 如果不在同一线程中释放，或者线程缓存已满，同步地将池块返回给其父池
            synchronized (this) {
                chunk.parent.free(chunk, handle);
            }
        }
    }


    /**
     * 根据元素大小查找并返回对应的子页池头部。
     *
     * @param elemSize 元素的大小，用于确定子页池的索引。
     * @return 返回对应元素大小的子页池头部。
     */
    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        /* 索引值，用于定位到具体的子页池数组 */
        int tableIdx;
        /* 子页池数组，根据元素大小的不同，指向tinySubpagePools或smallSubpagePools */
        PoolSubpage<T>[] table;

        /* 判断元素大小是否属于tiny类别 */
        if (isTiny(elemSize)) {
            /* 计算tiny元素大小的索引 */
            tableIdx = elemSize >>> 4;
            /* 使用tiny元素大小的子页池数组 */
            table = tinySubpagePools;
        } else {
            /* 初始化索引为0，用于后续计算small元素大小的索引 */
            tableIdx = 0;
            /* 将元素大小右移10位，用于small元素大小的索引计算 */
            elemSize >>>= 10;
            /* 循环计算small元素大小的索引 */
            while (elemSize != 0) {
                /* 将元素大小再次右移1位，索引加1 */
                elemSize >>>= 1;
                tableIdx++;
            }
            /* 使用small元素大小的子页池数组 */
            table = smallSubpagePools;
        }

        /* 根据计算得到的索引，返回对应的子页池头部 */
        return table[tableIdx];
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
     * 判断容量是否为微小或小型。
     *
     * 本方法通过位运算检查给定容量是否满足微小或小型的条件。具体来说，它检查给定容量是否没有超过子页面的溢出阈值。
     * 这种检查对于内存管理特别是内存分配策略来说是关键的，因为它决定了容量是否可以被归类为微小或小型，
     * 进而影响内存分配的决策和效率。
     *
     * @param normCapacity 正常化的容量值，表示需要检查的容量。
     * @return 如果容量没有超过子页面的溢出阈值，则返回true，表示该容量是微小或小型的；否则返回false。
     */
    boolean isTinyOrSmall(int normCapacity) {
        // 使用位与操作符检查给定容量是否没有超过子页面的溢出阈值。
        return (normCapacity & subpageOverflowMask) == 0;
    }


    /**
     * 判断给定的容量是否为小容量。
     *
     * 本函数通过位运算检查给定的容量值是否满足小容量的定义。小容量具体指容量值的二进制表示中，
     * 最高3位为0的情况。这用于内部的容量管理，可能用于决定使用哪种策略进行内存分配。
     *
     * @param normCapacity 容量值，一个整数。
     * @return 如果容量值是小容量，则返回true；否则返回false。
     */
    static boolean isTiny(int normCapacity) {
        // 通过与操作和特定的位模式检查normCapacity的最高3位是否为0
        return (normCapacity & 0xFFFFFE00) == 0;
    }

    /**
     * 根据请求的容量为缓冲区分配内存。
     * 此方法决定是从小、大还是巨大的池中分配内存，或者直接分配正常大小的内存块。
     * 它使用缓存尝试分配内存，如果缓存无法分配，则尝试从子页池中分配。
     * 如果所有尝试都失败，则会尝试分配一个巨大的缓冲区。
     *
     * @param cache    当前线程的缓存，用于尝试首先从缓存中分配内存。
     * @param buf      将要分配内存的缓冲区对象。
     * @param reqCapacity 请求的容量，此方法将根据此值决定如何分配内存。
     */
    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
        // 标准化容量，确保容量值在可接受的范围内。
        final int normCapacity = normalizeCapacity(reqCapacity);

        // 判断请求的容量是否属于小或小的范围。
        if (isTinyOrSmall(normCapacity)) {
            int tableIdx;
            PoolSubpage<T>[] table;

            // 如果请求的容量属于微小的范围。
            if (isTiny(normCapacity)) {
                // 尝试从缓存中分配微小的缓冲区，如果成功则直接返回。
                if (cache.allocateTiny(buf, reqCapacity, normCapacity)) {

                    return;
                }
                // 计算微小缓冲区子页池的索引，并获取对应的子页池数组。
                tableIdx = tinyIdx(normCapacity);
                table = tinySubpagePools;
            } else {
                // 尝试从缓存中分配小的缓冲区，如果成功则直接返回。
                if (cache.allocateSmall(buf, reqCapacity, normCapacity)) {

                    return;
                }
                // 计算小缓冲区子页池的索引，并获取对应的子页池数组。
                tableIdx = smallIdx(normCapacity);
                table = smallSubpagePools;
            }

            // 尝试从同步块中分配内存，以避免并发问题。
            synchronized (this) {
                // 获取对应索引的子页池头节点。
                final PoolSubpage<T> head = table[tableIdx];
                // 获取头节点的下一个节点，即第一个可用的子页。
                final PoolSubpage<T> s = head.next;
                // 如果存在可用的子页，则尝试从子页中分配内存。
                if (s != head) {
                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubpage(buf, handle, reqCapacity);
                    return;
                }
            }
        } else if (normCapacity <= chunkSize) {
            // 如果请求的容量在正常范围内，尝试从缓存中分配正常的缓冲区。
            if (cache.allocateNormal(buf, reqCapacity, normCapacity)) {

                return;
            }
        } else {
            // 如果请求的容量过大，尝试分配一个巨大的缓冲区。

            allocateHuge(buf, reqCapacity);
            return;
        }
        // 如果所有尝试都失败，则尝试分配一个正常的缓冲区。
        allocateNormal(buf, reqCapacity, normCapacity);
    }


    /**
     * 分配一个普通的池化字节缓冲区。
     *
     * 此方法尝试从不同的大小分区（q050, q025, q000, qInit, q075, q100）中分配一个缓冲区，
     * 如果没有合适的缓冲区，则创建一个新的chunk并分配一个缓冲区。
     *
     * @param buf 要分配的池化字节缓冲区对象。
     * @param reqCapacity 缓冲区的请求容量。
     * @param normCapacity 缓冲区的规范容量。
     */
    private synchronized void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        // 尝试从不同大小的队列中分配缓冲区，直到成功分配或尝试所有队列
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
                q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
                q075.allocate(buf, reqCapacity, normCapacity) || q100.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }

        // 如果所有队列都无法分配，则创建一个新的chunk
        PoolChunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
        long handle = c.allocate(normCapacity);
        // 确保分配成功
        assert handle > 0;
        c.initBuf(buf, handle, reqCapacity);
        // 将新chunk添加到初始化队列
        qInit.add(c);
    }


    /**
     * 专门为分配大块内存而设计的方法。
     * <p>
     * 此方法用于初始化一个池化字节缓冲区，使用未池化的块来满足大于常规池化块大小的内存分配需求。
     * 它避免了大块内存被池化可能导致的内存碎片化和效率低下问题。
     * <p>
     * 方法通过调用 {@link PooledByteBuf#initUnpooled} 方法，使用新创建的未池化块来初始化给定的池化字节缓冲区，
     * 并确保缓冲区的容量满足特定的需求。
     *
     * @param buf     需要初始化的池化字节缓冲区，它将使用新分配的未池化块。
     * @param reqCapacity 需要的缓冲区容量，此容量将被用于新分配的未池化块。
     */
    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        buf.initUnpooled(newUnpooledChunk(reqCapacity), reqCapacity);
    }

    /**
     * 标准化容量以确保其为有效值，并针对不同情况优化容量分配。
     *
     * @param reqCapacity 需要的容量
     * @return 标准化后的容量
     * @throws IllegalArgumentException 如果请求的容量小于0
     */
    private int normalizeCapacity(int reqCapacity) {
        // 检查容量是否小于0，如果是，则抛出异常
        if (reqCapacity < 0) {
            throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
        }
        // 如果请求的容量大于等于块大小，则直接返回请求的容量
        if (reqCapacity >= chunkSize) {
            return reqCapacity;
        }

        // 对于大容量，使用位操作优化容量计算，确保容量是2的幂次方
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

            // 如果计算结果为负数，说明超过了int的最大值，右移一位以修正
            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }
            return normalizedCapacity;
        }
        // 对于小容量，确保其为量子空间内的值
        // Quantum-spaced
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }

        // 对于小容量，如果不在量子空间内，调整至下一个量子空间的起始位置
        return (reqCapacity & ~15) + 16;
    }


    /**
     * 创建并初始化一个新的子页面池头部。
     *
     * @param pageSize 每个子页面的大小。这个参数决定了子页面能够存储的对象数量。
     * @return 返回一个新的、自引用的子页面池头部。这个头部同时指向自己作为开始和结束的标志，
     *         代表了一个空的、尚未使用的子页面链表。
     */
    private PoolSubpage<T> newSubpagePoolHead(int pageSize) {
        // 创建一个新的子页面对象，用于作为池的头部。
        PoolSubpage<T> head = new PoolSubpage<T>(pageSize);

        // 初始化头部的前一个和下一个引用都指向自身，因为此时链表为空，头部既是开始也是结束。
        head.prev = head;
        head.next = head;

        // 返回新创建并初始化好的头部对象。
        return head;
    }


    /**
     * 创建一个PoolSubpage数组，用于存储子页面。
     *
     * @param size 数组的大小，决定了可以存储的子页面数量。
     * @return 返回一个未初始化的PoolSubpage数组，长度为参数size。
     * @SuppressWarnings("unchecked") 由于此方法内部明确知道数组的类型，因此忽略了泛型检查的警告。
     */
    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpagePoolArray(int size) {
        return new PoolSubpage[size];
    }



    /**
     * 创建一个新的PoolChunk对象。这个方法是抽象的，需要在子类中实现。
     * PoolChunk是对象池管理内存块的核心数据结构之一。
     *
     * @param pageSize 每个页的大小。这是对象池管理内存的最小单位。
     * @param maxOrder 页索引的最大位数。这决定了对象池能够管理的最大内存大小。
     * @param pageShifts 用于计算页大小的位移量。这是基于2的幂次运算的优化。
     * @param chunkSize 一个chunk的总大小。chunk是对象池管理内存的基本单元。
     * @return 返回一个新的PoolChunk实例，这个实例用于管理特定大小的内存块。
     */
    protected abstract PoolChunk<T> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);

    /**
     * 创建一个新的未池化块。
     * <p>
     * 该方法用于在内存池中创建一个新的、未被池化的块。子类需要实现此方法来定义如何创建具有特定容量的块。
     * 这个方法的设计是为了允许具体的内存池实现决定如何管理未池化的内存块。这些块可能会在后续被转换成池化块，
     * 或者直接用于满足特定大小的分配请求。
     *
     * @param capacity 新块的容量。容量是指块能够分配的最大字节大小。
     * @return 一个新创建的、未池化的块。
     */
    protected abstract PoolChunk<T> newUnpooledChunk(int capacity);

    /**
     * 创建一个新的PooledByteBuf实例。
     *
     * 该方法是抽象的，需要在子类中实现，以根据特定的需要返回一个PooledByteBuf实例。
     * 它的目的是为了在池中分配一个新的字节缓冲区，最大容量由参数maxCapacity指定。
     * 实现这个方法时，应该考虑如何最有效地管理内存，以提高性能和减少内存浪费。
     *
     * @param maxCapacity 新分配的字节缓冲区的最大容量。
     *                    这个参数是为了确保创建的缓冲区能够满足最大预期需求，
     *                    同时避免过度分配内存。
     * @return 返回一个新的PooledByteBuf实例，该实例的最大容量不超过maxCapacity。
     */
    protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);

    /**
     * 抽象方法，用于实现从源对象到目标对象的内存复制功能。
     * 此方法需要在子类中具体实现，以支持不同类型的内存复制操作。
     *
     * @param src 源对象，内存将从该对象复制。
     * @param srcOffset 源对象中的起始偏移量，从该位置开始复制内存。
     * @param dst 目标对象，内存将被复制到该对象。
     * @param dstOffset 目标对象中的起始偏移量，内存将从该位置开始被写入。
     * @param length 要复制的内存长度。
     */
    protected abstract void memoryCopy(T src, int srcOffset, T dst, int dstOffset, int length);

    /**
     * 销毁一个池块。
     *
     * 此抽象方法定义了如何销毁池中的一个块。池块是对象池管理内存的一种方式，
     * 它通常包含一组预分配的对象，这些对象可以在需要时快速分配给用户。
     * 销毁池块意味着将其从池中移除，并可能释放其占用的物理内存。
     *
     * @param chunk 要销毁的池块。这个参数是一个具体的池块对象，它包含了需要被销毁的
     *              内存块的相关信息。子类需要实现此方法来定义具体的销毁逻辑。
     */
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

    /**
     * HeapArena类是PoolArena<byte[]>的子类，专门用于管理堆内存分配。
     * 它是框架中处理内存分配的重要组成部分，主要负责byte[]数组的分配和回收。
     * 与堆外内存相比，堆内存分配更简单，但可能引起垃圾收集的更多开销。
     */
    public static final class HeapArena extends PoolArena<byte[]> {

        /**
         * HeapArena的构造函数。
         * <p>
         * HeapArena是PooledByteBufAllocator的一个组件，负责管理堆内存的分配和回收。这个构造函数被用于初始化HeapArena实例。
         * <p>
         * 参数:
         * parent - 父分配器，通常是PooledByteBufAllocator实例，用于管理多个Arena。
         * pageSize - 每个页的大小。这是分配堆内存的基本单位。
         * maxOrder - 页大小的最大幂次。用于确定可以分配的最大内存块大小。
         * pageShifts - 与pageSize相关的位移量，用于计算内存块的地址。
         * chunkSize - 每个chunk的大小。chunk是管理内存分配的基本单元。
         * <p>
         * 注意：这个构造函数是HeapArena内部逻辑的一部分，通常不直接由用户调用。
         */
        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize);
        }

        /**
         * 创建一个新的PoolChunk实例。
         * PoolChunk是对象池中管理内存块的实体，它包含了一块指定大小的内存以及相关元数据。
         * 本方法用于在对象池需要扩展时，创建新的内存块。
         *
         * @param pageSize 每页的大小。决定了每个内存块被分割成的页的数量。
         * @param maxOrder 页索引的最大位数。用于计算对象池能够管理的最大内存块大小。
         * @param pageShifts 页大小的位移量。用于快速计算页索引对应的内存偏移量。
         * @param chunkSize 内存块的总大小。即这个PoolChunk管理的内存区域的大小。
         * @return 返回一个新的PoolChunk实例，该实例用于管理一块指定大小的内存。
         */
        @Override
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            // 使用本类的构造函数创建一个新的PoolChunk实例。
            // 参数包括本对象、一块新分配的内存数组、每页的大小、页索引的最大位数、页大小的位移量和内存块的总大小。
            return new PoolChunk<byte[]>(this, new byte[chunkSize], pageSize, maxOrder, pageShifts, chunkSize);
        }


        /**
         * 创建一个新的未池化的块。
         * 当需要一个新的、未被池化管理的内存块时，这个方法会被调用。它主要用于内部内存管理，
         * 为指定的容量分配一个新的字节数组，并将其包装在一个PoolChunk实例中。
         *
         * @param capacity 内存块的容量。这个参数指定了新分配的字节数组的大小。
         * @return 返回一个新的PoolChunk实例，它包含了一个新分配的、未被池化的字节数组。
         */
        @Override
        protected PoolChunk<byte[]> newUnpooledChunk(int capacity) {
            // 使用指定的容量创建一个新的字节数组，并将其与当前的池配置包装在一个新的PoolChunk实例中。
            return new PoolChunk<byte[]>(this, new byte[capacity], capacity);
        }


        @Override
        protected void destroyChunk(PoolChunk<byte[]> chunk) {
            // 依靠GC。
        }

        /**
         * 创建一个新的字节缓冲区实例。
         * 此方法被覆盖以指定当需要一个新的缓冲区实例时，应该使用哪种类型的缓冲区。
         * 它是池化字节缓冲区工厂的核心方法之一，用于按需分配缓冲区。
         *
         * @param maxCapacity 新缓冲区的最大容量。指定最大容量是为了确保缓冲区能够满足不同大小的数据存储需求。
         * @return 返回一个新创建的池化堆字节缓冲区实例，该实例的最大容量为指定值。
         */
        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            // 使用PooledHeapByteBuf的静态工厂方法创建一个新的池化堆字节缓冲区实例。
            // 选择堆缓冲区是因为它在某些场景下比直接内存缓冲区更具性能优势或更易管理。
            return PooledHeapByteBuf.newInstance(maxCapacity);
        }

        /**
         * 实现内存拷贝功能。
         * 该方法重写了父类中的memoryCopy方法，使用System.arraycopy实现字节数组之间的拷贝。
         *
         * @param src 源字节数组，包含需要拷贝的数据。
         * @param srcOffset 源数组中开始拷贝的索引。
         * @param dst 目标字节数组，接收拷贝的数据。
         * @param dstOffset 目标数组中开始写入的索引。
         * @param length 需要拷贝的字节长度。
         */
        @Override
        protected void memoryCopy(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
            // 如果需要拷贝的长度为0，则无需执行拷贝操作，直接返回。
            if (length == 0) {
                return;
            }

            // 使用System.arraycopy进行字节数组拷贝，从源数组的srcOffset位置开始拷贝length长度的数据到目标数组的dstOffset位置。
            System.arraycopy(src, srcOffset, dst, dstOffset, length);
        }

    }

}
