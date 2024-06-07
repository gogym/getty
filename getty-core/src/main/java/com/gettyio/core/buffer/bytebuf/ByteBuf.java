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
package com.gettyio.core.buffer.bytebuf;


import com.gettyio.core.buffer.allocator.ByteBufAllocator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SuppressWarnings("ClassMayBeInterface")
public abstract class ByteBuf implements ReferenceCounted {

    /**
     * 返回该缓冲区可以包含的字节数
     */
    public abstract int capacity();


    /**
     * 设置缓冲区的容量。
     * <p>此方法是抽象方法，需要在子类中实现具体的容量设置逻辑。它的目的是为了调整缓冲区的容量，以适应不同的数据处理需求。</p>
     *
     * @param newCapacity 指定的新容量。新容量应该大于等于当前已使用容量。
     * @return 返回容量被修改后的缓冲区实例。通常，这会是调用此方法的实例本身，以支持方法链式调用。
     * @throws IllegalArgumentException 如果指定的容量小于当前已使用容量，则抛出此异常。
     */
    public abstract ByteBuf capacity(int newCapacity);

    /**
     * 返回此缓冲区的最大允许容量。如果用户试图增加
     * 使用{@link # Capacity (int)}或
     * {@link IllegalArgumentException}。
     */
    public abstract int maxCapacity();

    /**
     * 返回创建此缓冲区的{@link ByteBufAllocator}。
     */
    public abstract ByteBufAllocator alloc();

    /**
     * 返回该缓冲区的字节顺序。
     * <p>此方法是一个抽象方法，需要在子类中实现，以返回特定于实现的字节顺序。字节顺序对于处理多字节数据类型（如int或long）时非常重要，它定义了这些数据类型中字节的排列方式。</p>
     *
     * @return 缓冲区的字节顺序。这可以是大端在前（Big-Endian）或小端在前（Little-Endian）。
     */
    public abstract ByteOrder order();


    /**
     * 如果该缓冲区是另一个缓冲区的包装器，则返回底层缓冲区实例。
     *
     * @return {@code null} 如果该缓冲区不是包装器
     */
    public abstract ByteBuf unwrap();

    /**
     * Returns the {@code readerIndex} of this buffer.
     * 返回该缓冲区的{@code readerIndex}。
     */
    public abstract int readerIndex();

    /**
     * 设置这个缓冲区的{@code readerIndex}。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code readerIndex}是
     *                                   小于{@code 0}或
     *                                   大于{@code this.writerIndex}
     */
    public abstract ByteBuf readerIndex(int readerIndex);

    /**
     * 返回该缓冲区的写下标
     */
    public abstract int writerIndex();

    /**
     * 返回该缓冲区的{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException if the specified {@code writerIndex} is
     *                                   less than {@code this.readerIndex} or
     *                                   greater than {@code this.capacity}
     */
    public abstract ByteBuf writerIndex(int writerIndex);

    /**
     * 设置index
     *
     * @param readerIndex 设置缓冲区读下标
     * @param writerIndex 设置缓冲区的写下标
     * @return 返回缓冲区本身ø
     */
    public abstract ByteBuf setIndex(int readerIndex, int writerIndex);

    /**
     * 返回的可读字节数
     */
    public abstract int readableBytes();

    /**
     * 返回的可写字节数
     */
    public abstract int writableBytes();

    /**
     * 返回 {@code true}  当且仅当{@code (this.writerIndex - this.readerIndex)} 大于{@code 0}
     */
    public abstract boolean isReadable();

    /**
     * Returns {@code true} if and only if this buffer contains equal to or more than the specified number of elements.
     * 当且仅当该缓冲区包含等于或大于指定数量的元素时返回{@code true}。
     */
    public abstract boolean isReadable(int size);

    /**
     * return true,当且仅当{@code (this.capacity - this.writerIndex)}大于{@code 0}
     */
    public abstract boolean isWritable();

    /**
     * Returns {@code true} if and only if this buffer has enough room to allow writing the specified number of elements.
     * 当且仅当该缓冲区有足够的空间允许写入指定数量的元素时返回{@code true}。
     */
    public abstract boolean isWritable(int size);

    /**
     * 设置该缓冲区的{@code readerIndex}和{@code writerIndex}为
     * {@code 0}。
     * 这个方法与{@link #setIndex(int, int) setIndex(0,0)}相同。
     * <p>
     * 请注意这个方法的行为是不同的
     * 从NIO缓冲区，它设置{@code 限制}为
     * 缓冲区的{@code 容量}。
     */
    public abstract ByteBuf clear();

    /**
     * 获取缓冲区中指定的绝对{@code index}处的字节。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code index}小于{@code 0}或
     *                                   {@code index + 1}大于{@code this.capacity}
     */
    public abstract byte getByte(int index);

    /**
     * 在指定的绝对{@code index}处获取一个32位整数
     * 这个缓冲区。此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code index}小于{@code 0}或
     *                                   {@code index + 1}大于{@code this.capacity}
     */
    public abstract int getInt(int index);

    /**
     * 将缓冲区的数据传输到指定的目的地
     * 指定的绝对{@code index}。
     * 此方法不修改{@code readerIndex}或{@code writerIndex}
     * 源(即{@code this})和目标的。
     *
     * @param dstIndex 目的地的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException 如果指定的{@code index}小于{@code 0}，
     *                                   如果指定的{@code dstIndex}小于{@code 0}，
     *                                   如果{@code index + length}大于{@code this. length}。或者如果{@code dstIndex + length}大于{@code dst.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    /**
     * 从指定的绝对{@code index}开始，将该缓冲区的数据传输到指定的目的地。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param dstIndex 目的地的第一个索引
     * @param length   要传输的字节数
     */
    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * 从指定的绝对{@code index}开始将该缓冲区的数据传输到指定的目的地，直到目的地的位置达到其限制。
     * 当目标的{@code position}增加时，此方法不会修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     */
    public abstract ByteBuf getBytes(int index, ByteBuffer dst);

    /**
     * 从指定的绝对{@code index}开始，将该缓冲区的数据传输到指定的流。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length 要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   if {@code index + length} is greater than
     *                                   {@code this.capacity}
     * @throws IOException               如果指定的流在I/O期间抛出异常
     */
    public abstract ByteBuf getBytes(int index, OutputStream out, int length) throws IOException;


    /**
     * 从指定的绝对{@code index}开始，将指定源缓冲区的数据传输到此缓冲区。
     * 这个方法不会修改源(即{@code This})和目标的{@code readerIndex}或{@code writerIndex}。
     *
     * @param srcIndex 源的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0},
     *                                   if the specified {@code srcIndex} is less than {@code 0},
     *                                   if {@code index + length} is greater than
     *                                   {@code this.capacity}, or
     *                                   if {@code srcIndex + length} is greater than
     *                                   {@code src.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    /**
     * 从指定的绝对{@code index}开始，将指定的源数组的数据传输到这个缓冲区。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0},
     *                                   if the specified {@code srcIndex} is less than {@code 0},
     *                                   if {@code index + length} is greater than
     *                                   {@code this.capacity}, or
     *                                   if {@code srcIndex + length} is greater than {@code src.length}
     */
    public abstract ByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    /**
     * 从指定的绝对{@code index}开始将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区的位置达到它的限制。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   if {@code index + src.remaining()} is greater than
     *                                   {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuffer src);

    /**
     * 从指定的绝对{@code index}开始将指定源流的内容传输到此缓冲区。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length 要传输的字节数
     * @return 从指定通道读入的实际字节数。
     * {@code -1} 如果指定的通道关闭。
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException               if the specified stream threw an exception during I/O
     */
    public abstract int setBytes(int index, InputStream in, int length) throws IOException;

    /**
     * 获取当前{@code readerIndex}处的一个字节，并在此缓冲区中将{@code readerIndex}的值增加{@code 1}
     *
     * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract byte readByte();

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，直到目的地变为
     * 不可写，并将{@code readerIndex}增加传输的字节数。
     * 除了这个方法增加目标的{@code writerIndex}的传输字节数
     *
     * @throws IndexOutOfBoundsException if {@code dst.writableBytes} is greater than
     *                                   {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst);

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，并将{@code readerIndex}增加传输的字节数(= {@code dst.length})。
     *
     * @throws IndexOutOfBoundsException if {@code dst.length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(byte[] dst);

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，并将{@code readerIndex}增加传输的字节数(= {@code length})。
     *
     * @param dstIndex 目的地的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code dstIndex} is less than {@code 0},
     *                                   if {@code length} is greater than {@code this.readableBytes}, or
     *                                   if {@code dstIndex + length} is greater than {@code dst.length}
     */
    public abstract ByteBuf readBytes(byte[] dst, int dstIndex, int length);

    /**
     * 从当前{@code readerIndex}开始将该缓冲区的数据传输到指定的目的地，直到目的地的位置达到其限制，并增加{@code readerIndex}的传输字节数。
     *
     * @throws IndexOutOfBoundsException if {@code dst.remaining()} is greater than
     *                                   {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuffer dst);

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的流。
     *
     * @param length 要传输的字节数
     * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException               if the specified stream threw an exception during I/O
     */
    public abstract ByteBuf readBytes(OutputStream out, int length) throws IOException;


    /**
     * 从当前{@code writerIndex}开始，将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区变得不可读，并将{@code writerIndex}增加传输的字节数。
     * 这个方法与{@link #writeBytes(ByteBuf, int, int)}基本相同，除了这个方法增加源缓冲区的{@code readerIndex}的传输字节数，
     * 而{@link #writeBytes(ByteBuf, int, int)}没有。
     *
     * @throws IndexOutOfBoundsException if {@code src.readableBytes} is greater than
     *                                   {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src);

    /**
     * 从当前{@code writerIndex}开始，将指定的源缓冲区的数据传输到这个缓冲区，并将{@code writerIndex}增加传输的字节数(= {@code length})。
     *
     * @param srcIndex 源的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code srcIndex} is less than {@code 0},
     *                                   if {@code srcIndex + length} is greater than
     *                                   {@code src.capacity}, or
     *                                   if {@code length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    /**
     * 将指定的源数组的数据从当前{@code writerIndex}开始传输到这个缓冲区，并将{@code writerIndex}增加传输的字节数(= {@code src.length})。
     *
     * @throws IndexOutOfBoundsException if {@code src.length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(byte[] src);

    /**
     * 从当前{@code writerIndex}开始，将指定的源缓冲区的数据传输到这个缓冲区，并将{@code writerIndex}增加传输的字节数(= {@code length})。
     *
     * @param srcIndex 源的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code srcIndex} is less than {@code 0},
     *                                   if {@code srcIndex + length} is greater than
     *                                   {@code src.length}, or
     *                                   if {@code length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);

    /**
     * 从当前{@code writerIndex}开始将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区的位置达到极限，并将{@code writerIndex}增加传输的字节数。
     *
     * @throws IndexOutOfBoundsException if {@code src.remaining()} is greater than
     *                                   {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuffer src);

    /**
     * 从当前{@code writerIndex}开始将指定流的内容传输到此缓冲区，并将{@code writerIndex}增加传输的字节数。
     *
     * @param length 要传输的字节数
     * @return 从指定流中读入的实际字节数
     * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.writableBytes}
     * @throws IOException               if the specified stream threw an exception during I/O
     */
    public abstract int writeBytes(InputStream in, int length) throws IOException;


    /**
     * 返回该缓冲区的可读字节的副本。修改的内容
     * 返回的缓冲区或该缓冲区之间完全不相互影响。
     * 这个方法与{@code buf.copy(buf.readerIndex()， buf.readableBytes())}相同。
     * 这个方法不会修改的{@code readerIndex}或{@code writerIndex}
     * 这个缓冲区。
     */
    public abstract ByteBuf copy();

    /**
     * 返回该缓冲区的子区域的副本。修改返回的缓冲区或该缓冲区的内容根本不会影响彼此。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     */
    public abstract ByteBuf copy(int index, int length);


    /**
     * 获取该缓冲区对应的nio缓存区，如果不存在将会调用 nioBuffer()赋值。
     * 当执行 nioBuffer() 时，这个缓冲区会被新的覆盖
     *
     * @return 对应的nio缓存区
     */
    public abstract ByteBuffer getNioBuffer();


    /**
     * 将该缓冲区的子区域暴露为NIO {@link ByteBuffer}。返回的缓冲区与该缓冲区共享内容，而改变返回的NIO缓冲区的位置和限制不影响该缓冲区的索引和标记。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态的，并且它调整了容量，那么返回的NIO缓冲区将看不到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     */
    public abstract ByteBuffer nioBuffer(int index, int length);

    /**
     * 返回由此内容计算的哈希码
     * 缓冲区。如果有一个字节数组
     * {@linkplain #equals(Object) equal to}这个数组，两个数组都应该
     * 返回相同的值。
     */
    @Override
    public abstract int hashCode();

    /**
     * 返回该缓冲区的字符串表示。这个方法没有
     * 必须返回缓冲区的全部内容，但返回
     * 键属性的值，如{@link #readerIndex()}，
     * {@link #writerIndex()}和{@link #capacity()}。
     */
    @Override
    public abstract String toString();

    @Override
    public abstract ByteBuf retain(int increment);

    @Override
    public abstract ByteBuf retain();

}
