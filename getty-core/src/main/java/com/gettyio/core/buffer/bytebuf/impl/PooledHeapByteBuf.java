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


import com.gettyio.core.buffer.bytebuf.ByteBuf;
import com.gettyio.core.buffer.pool.Recycler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * 池化的堆内缓冲区
 */
public final class PooledHeapByteBuf extends PooledByteBuf<byte[]> {

    /**
     * 使用Recycler来管理PooledHeapByteBuf实例，以实现内存的复用，减少垃圾收集的压力。
     * 这里通过匿名内部类的方式实现了Recycler<PooledHeapByteBuf>的定制。
     * 当需要一个新的PooledHeapByteBuf实例时，Recycler会提供一个已经初始化过的对象，
     * 而不是创建一个新的对象，这样可以减少内存分配的开销。
     */
    private static final Recycler<PooledHeapByteBuf> RECYCLER = new Recycler<PooledHeapByteBuf>() {
        /**
         * 当Recycler需要一个新的PooledHeapByteBuf对象时，调用此方法。
         * 该方法使用了延迟初始化的策略，只有在真正需要时才会创建对象。
         *
         * @param handle 回收器的句柄，用于管理这个对象的生命周期。
         * @return 返回一个新的或已回收的PooledHeapByteBuf实例。
         */
        @Override
        protected PooledHeapByteBuf newObject(Handle<PooledHeapByteBuf> handle) {
            return new PooledHeapByteBuf(handle, 0);
        }
    };


    /**
     * 创建一个新实例的PooledHeapByteBuf。
     *
     * 该方法通过重用机制获取一个PooledHeapByteBuf实例，避免了对象的频繁创建和销毁，从而提高了性能。
     * 如果没有可用的实例，将会创建一个新的实例。
     *
     * @param maxCapacity 指定新实例的最大容量。这个参数用于确保新创建的ByteBuf能够容纳指定数量的字节，
     *                    从而避免了在后续操作中需要频繁扩容的问题。
     * @return 返回一个新实例的PooledHeapByteBuf。这个实例已经初始化了最大容量和引用计数，
     *         可以直接使用。
     */
    public static PooledHeapByteBuf newInstance(int maxCapacity) {
        PooledHeapByteBuf buf = RECYCLER.get();
        buf.setRefCnt(1);
        buf.maxCapacity(maxCapacity);
        return buf;
    }

    private PooledHeapByteBuf(Recycler.Handle<PooledHeapByteBuf> recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    @Override
    protected byte _getByte(int index) {
        return memory[idx(index)];
    }


    @Override
    protected int _getInt(int index) {
        index = idx(index);
        return (memory[index] & 0xff) << 24 |
                (memory[index + 1] & 0xff) << 16 |
                (memory[index + 2] & 0xff) << 8 |
                memory[index + 3] & 0xff;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        dst.setBytes(dstIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(memory, idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        dst.put(memory, idx(index), Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        out.write(memory, idx(index), length);
        return this;
    }


    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        src.getBytes(srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        src.get(memory, idx(index), length);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        return in.read(memory, idx(index), length);
    }


    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().buffer(length, maxCapacity());
        copy.writeBytes(memory, idx(index), length);
        return copy;
    }


    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer buf = ByteBuffer.wrap(memory, index, length).slice();
        byteBuffer = buf;
        return buf;
    }

}
