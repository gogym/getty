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
     * System.out.println(Integer.toBinaryString(512 >>> 4));
     * 内存池子页池子大小
     */
    static final int numTinySubpagePools = 512 >>> 4; //100000
    /**
     * 池化缓冲区构造器
     */
    public final PooledByteBufAllocator parent;
    /**
     * 平衡二叉树的深度
     */
    private final int maxOrder;
    /**
     * 每个PoolSubpage的大小
     */
    final int pageSize;
    /**
     * pageSize 2的 pageShifts幂
     */
    final int pageShifts;
    /**
     * PoolChunk的总内存大小
     */
    final int chunkSize;
    /**
     * 用来判断申请的内存是否超过pageSize的掩码，等于  ~(pageSize-1)
     */
    final int subpageOverflowMask;
    final int numSmallSubpagePools;

    /**
     * 一组连续的内存空间
     */
    private final PoolSubpage<T>[] tinySubpagePools;
    private final PoolSubpage<T>[] smallSubpagePools;

    /**
     * 不同使用频率的内存块列表
     */
    private final PoolChunkList<T> q100;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;

    protected PoolArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        subpageOverflowMask = ~(pageSize - 1);
        tinySubpagePools = newSubpagePoolArray(numTinySubpagePools);
        for (int i = 0; i < tinySubpagePools.length; i++) {
            tinySubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        numSmallSubpagePools = pageShifts - 9;
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);
        for (int i = 0; i < smallSubpagePools.length; i++) {
            smallSubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE);
        q075 = new PoolChunkList<T>(this, q100, 75, 100);
        q050 = new PoolChunkList<T>(this, q075, 50, 100);
        q025 = new PoolChunkList<T>(this, q050, 25, 75);
        q000 = new PoolChunkList<T>(this, q025, 1, 50);
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25);

        q100.prevList = q075;
        q075.prevList = q050;
        q050.prevList = q025;
        q025.prevList = q000;
        q000.prevList = null;
        qInit.prevList = qInit;
    }

    PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    public void reallocate(PooledByteBuf<T> buf, int newCapacity, boolean freeOldMemory) {
        if (newCapacity < 0 || newCapacity > buf.maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int oldCapacity = buf.length;
        if (oldCapacity == newCapacity) {
            return;
        }

        PoolChunk<T> oldChunk = buf.chunk;
        long oldHandle = buf.handle;
        T oldMemory = buf.memory;
        int oldOffset = buf.offset;
        int oldMaxLength = buf.maxLength;
        int readerIndex = buf.readerIndex();
        int writerIndex = buf.writerIndex();

        allocate(parent.threadCache.get(), buf, newCapacity);
        if (newCapacity > oldCapacity) {
            memoryCopy(
                    oldMemory, oldOffset,
                    buf.memory, buf.offset, oldCapacity);
        } else if (newCapacity < oldCapacity) {
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex = newCapacity;
                }
                memoryCopy(
                        oldMemory, oldOffset + readerIndex,
                        buf.memory, buf.offset + readerIndex, writerIndex - readerIndex);
            } else {
                readerIndex = writerIndex = newCapacity;
            }
        }

        buf.setIndex(readerIndex, writerIndex);

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

    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubpage<T>[] table;
        // < 512
        if (isTiny(elemSize)) {
            tableIdx = elemSize >>> 4;
            table = tinySubpagePools;
        } else {
            tableIdx = 0;
            elemSize >>>= 10;
            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx++;
            }
            table = smallSubpagePools;
        }

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
