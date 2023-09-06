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
package com.gettyio.core.buffer.bytebuf.impl;

import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.buffer.bytebuf.AbstractReferenceCountedByteBuf;
import com.gettyio.core.buffer.bytebuf.ByteBuf;
import com.gettyio.core.buffer.pool.PoolChunk;
import com.gettyio.core.buffer.pool.Recycler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 池化的缓冲区基类
 *
 * @param <T>
 */
public abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {
    /**
     * 回收对象处理器
     */
    private final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;
    /**
     * 当前缓冲区所属的chunk
     */
    public PoolChunk<T> chunk;
    /**
     * 标记是否已经回收
     * handle < 0 则被回收
     */
    public long handle;
    /**
     * 存储对象
     */
    public T memory;
    /**
     * 在内存页上的偏移量
     */
    public int offset;
    /**
     * 缓冲区大小 capacity()
     */
    public int length;
    /**
     * 最大长度
     */
    public int maxLength;

    /**
     * 初始化时的线程，用于判断是否可以被释放
     */
    public Thread initThread;


    @SuppressWarnings("unchecked")
    protected PooledByteBuf(Recycler.Handle<? extends PooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (Recycler.Handle<PooledByteBuf<T>>) recyclerHandle;
    }

    public void init(PoolChunk<T> chunk, long handle, int offset, int length, int maxLength) {
        assert handle >= 0;
        assert chunk != null;

        this.chunk = chunk;
        this.handle = handle;
        memory = chunk.memory;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
        setIndex(0, 0);
        initThread = Thread.currentThread();
    }

    public void initUnpooled(PoolChunk<T> chunk, int length) {
        assert chunk != null;

        this.chunk = chunk;
        handle = 0;
        memory = chunk.memory;
        offset = 0;
        this.length = maxLength = length;
        setIndex(0, 0);
        initThread = Thread.currentThread();
    }

    @Override
    public final int capacity() {
        return length;
    }

    @Override
    public final ByteBuf capacity(int newCapacity) {
        ensureAccessible();

        // 如果请求容量不需要重新分配，只需更新内存长度。
        if (chunk.unpooled) {
            if (newCapacity == length) {
                return this;
            }
        } else {
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity < length) {
                if (newCapacity > maxLength >>> 1) {
                    if (maxLength <= 512) {
                        if (newCapacity > maxLength - 16) {
                            length = newCapacity;
                            setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                            return this;
                        }
                    } else { // > 512 (i.e. >= 1024)
                        length = newCapacity;
                        setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                        return this;
                    }
                }
            } else {
                return this;
            }
        }

        // 需要重新分配。
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return chunk.arena.parent;
    }

    @Override
    public final ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public final ByteBuf unwrap() {
        return null;
    }

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            byteBuffer = null;
            memory = null;
            boolean sameThread = initThread == Thread.currentThread();
            initThread = null;
            chunk.arena.free(chunk, handle, maxLength, sameThread);
            recycle();
        }
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    protected final int idx(int index) {
        return offset + index;
    }
}
