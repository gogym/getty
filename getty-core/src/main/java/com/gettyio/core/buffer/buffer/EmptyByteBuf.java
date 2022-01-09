/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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

package com.gettyio.core.buffer.buffer;


import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.util.PlatformDependent;
import com.gettyio.core.util.StringUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

/**
 * 空的{@link ByteBuf}，其容量和最大容量均为{@code 0}。
 */
public final class EmptyByteBuf extends ByteBuf {

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocateDirect(0);
    /**
     * 空缓冲区地址
     */
    private static final long EMPTY_BYTE_BUFFER_ADDRESS;

    static {
        long emptyByteBufferAddress = 0;
        try {
            if (PlatformDependent.hasUnsafe()) {
                emptyByteBufferAddress = PlatformDependent.directBufferAddress(EMPTY_BYTE_BUFFER);
            }
        } catch (Throwable t) {
            // Ignore
        }
        EMPTY_BYTE_BUFFER_ADDRESS = emptyByteBufferAddress;
    }

    private final ByteBufAllocator alloc;
    private final ByteOrder order;
    private final String str;

    public EmptyByteBuf(ByteBufAllocator alloc) {
        this(alloc, ByteOrder.BIG_ENDIAN);
    }

    private EmptyByteBuf(ByteBufAllocator alloc, ByteOrder order) {
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }

        this.alloc = alloc;
        this.order = order;
        str = StringUtil.simpleClassName(this) + (order == ByteOrder.BIG_ENDIAN ? "BE" : "LE");
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return order;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public int maxCapacity() {
        return 0;
    }


    @Override
    public int readerIndex() {
        return 0;
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        return checkIndex(readerIndex);
    }

    @Override
    public int writerIndex() {
        return 0;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        return checkIndex(writerIndex);
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        checkIndex(readerIndex);
        checkIndex(writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return 0;
    }

    @Override
    public int writableBytes() {
        return 0;
    }

    @Override
    public int maxWritableBytes() {
        return 0;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public ByteBuf clear() {
        return this;
    }


    @Override
    public byte getByte(int index) {
        throw new IndexOutOfBoundsException();
    }


    @Override
    public int getInt(int index) {
        throw new IndexOutOfBoundsException();
    }


    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        return checkIndex(index, dst.writableBytes());
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        return checkIndex(index, dst.length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        return checkIndex(index, dst.remaining());
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) {
        return checkIndex(index, length);
    }


    @Override
    public ByteBuf setByte(int index, int value) {
        throw new IndexOutOfBoundsException();
    }


    @Override
    public ByteBuf setInt(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        return checkIndex(index, src.length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        return checkIndex(index, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        return checkIndex(index, src.remaining());
    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        checkIndex(index, length);
        return 0;
    }


    @Override
    public byte readByte() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readInt() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        return checkLength(dst.writableBytes());
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        return checkLength(dst.length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        return checkLength(dst.remaining());
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf writeByte(int value) {
        throw new IndexOutOfBoundsException();
    }


    @Override
    public ByteBuf writeInt(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        return checkLength(src.length);
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        return checkLength(length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        return checkLength(src.remaining());
    }

    @Override
    public int writeBytes(InputStream in, int length) {
        checkLength(length);
        return 0;
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        checkIndex(fromIndex);
        checkIndex(toIndex);
        return -1;
    }

    @Override
    public int bytesBefore(byte value) {
        return -1;
    }

    @Override
    public int bytesBefore(int length, byte value) {
        checkLength(length);
        return -1;
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        checkIndex(index, length);
        return -1;
    }


    @Override
    public ByteBuf copy() {
        return this;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return checkIndex(index, length);
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer getNioBuffer() {
        return EMPTY_BYTE_BUFFER;
    }

    @Override
    public ByteBuffer nioBuffer() {
        return EMPTY_BYTE_BUFFER;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return nioBuffer();
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return new ByteBuffer[]{EMPTY_BYTE_BUFFER};
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        return nioBuffers();
    }


    @Override
    public boolean hasMemoryAddress() {
        return EMPTY_BYTE_BUFFER_ADDRESS != 0;
    }

    @Override
    public long memoryAddress() {
        if (hasMemoryAddress()) {
            return EMPTY_BYTE_BUFFER_ADDRESS;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ByteBuf && !((ByteBuf) obj).isReadable();
    }

    @Override
    public String toString() {
        return str;
    }

    @Override
    public boolean isReadable(int size) {
        return false;
    }

    @Override
    public boolean isWritable(int size) {
        return false;
    }

    @Override
    public int refCnt() {
        return 1;
    }

    @Override
    public ByteBuf retain() {
        return this;
    }

    @Override
    public ByteBuf retain(int increment) {
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public boolean release(int decrement) {
        return false;
    }

    private ByteBuf checkIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException();
        }
        return this;
    }

    private ByteBuf checkIndex(int index, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (index != 0 || length != 0) {
            throw new IndexOutOfBoundsException();
        }
        return this;
    }

    private ByteBuf checkLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length + " (expected: >= 0)");
        }
        if (length != 0) {
            throw new IndexOutOfBoundsException();
        }
        return this;
    }
}
