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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 非池化的缓冲区，用完就会被释放
 */
public class UnpooledHeapByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;
    /**
     * 保存的数据
     */
    private byte[] array;

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param initialCapacity the initial capacity of the underlying byte array
     * @param maxCapacity     the max capacity of the underlying byte array
     */
    public UnpooledHeapByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        this(alloc, new byte[initialCapacity], 0, 0, maxCapacity);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param initialArray the initial underlying byte array
     * @param maxCapacity  the max capacity of the underlying byte array
     */
    protected UnpooledHeapByteBuf(ByteBufAllocator alloc, byte[] initialArray, int maxCapacity) {
        this(alloc, initialArray, 0, initialArray.length, maxCapacity);
    }

    private UnpooledHeapByteBuf(
            ByteBufAllocator alloc, byte[] initialArray, int readerIndex, int writerIndex, int maxCapacity) {

        super(maxCapacity);

        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialArray == null) {
            throw new NullPointerException("initialArray");
        }
        if (initialArray.length > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialArray.length, maxCapacity));
        }

        this.alloc = alloc;
        setArray(initialArray);
        setIndex(readerIndex, writerIndex);
    }

    private void setArray(byte[] initialArray) {
        array = initialArray;
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public int capacity() {
        ensureAccessible();
        return array.length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        ensureAccessible();
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int oldCapacity = array.length;
        if (newCapacity > oldCapacity) {
            byte[] newArray = new byte[newCapacity];
            System.arraycopy(array, 0, newArray, 0, array.length);
            setArray(newArray);
        } else if (newCapacity < oldCapacity) {
            byte[] newArray = new byte[newCapacity];
            int readerIndex = readerIndex();
            if (readerIndex < newCapacity) {
                int writerIndex = writerIndex();
                if (writerIndex > newCapacity) {
                    writerIndex(writerIndex = newCapacity);
                }
                System.arraycopy(array, readerIndex, newArray, readerIndex, writerIndex - readerIndex);
            } else {
                setIndex(newCapacity, newCapacity);
            }
            setArray(newArray);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        dst.setBytes(dstIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        ensureAccessible();
        dst.put(array, index, Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        ensureAccessible();
        out.write(array, index, length);
        return this;
    }


    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        src.getBytes(srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        ensureAccessible();
        src.get(array, index, src.remaining());
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        ensureAccessible();
        return in.read(array, index, length);
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        ensureAccessible();
        ByteBuffer buf = ByteBuffer.wrap(array, index, length).slice();
        byteBuffer = buf;
        return buf;
    }


    @Override
    public byte getByte(int index) {
        ensureAccessible();
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return array[index];
    }

    @Override
    public int getInt(int index) {
        ensureAccessible();
        return _getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return (array[index] & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) << 8 |
                array[index + 3] & 0xff;
    }


    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new UnpooledHeapByteBuf(alloc(), copiedArray, maxCapacity());
    }


    @Override
    protected void deallocate() {
        array = null;
        byteBuffer = null;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }
}
