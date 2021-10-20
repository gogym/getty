package com.gettyio.core.buffer.buffer;


import com.gettyio.core.buffer.ReferenceCounted;
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
     * 调整此缓冲区的容量。如果{@code newCapacity}小于当前
     * 容量，此缓冲区的内容被截断。如果{@code newCapacity}更大
     * 如果缓冲区的容量大于当前容量，则会追加长度为的未指定数据
     * {@code (newCapacity - currentCapacity)}。
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
     * 返回该缓冲区的值。
     */
    public abstract ByteOrder order();

    /**
     * 如果该缓冲区是另一个缓冲区的包装器，则返回底层缓冲区实例。
     *
     * @return {@code null} 如果该缓冲区不是包装器
     */
    public abstract ByteBuf unwrap();

    /**
     * 返回{@code true}当且仅当该缓冲区由NIO直接缓冲(堆外内存)
     */
    public abstract boolean isDirect();

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
     * 返回该缓冲区的{@code writerIndex}。
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
     * @param readerIndex
     * @param writerIndex
     * @return
     */
    public abstract ByteBuf setIndex(int readerIndex, int writerIndex);

    /**
     * 返回的可读字节数
     * {@code(this.writerIndex - this.readerIndex)}。
     */
    public abstract int readableBytes();

    /**
     * 返回的可写字节数
     * {@code (this.capacity - this.writerIndex)}.
     */
    public abstract int writableBytes();

    /**
     * 返回可写字节的最大可能数目
     * {@code (this.maxCapacity - this.writerIndex)}.
     */
    public abstract int maxWritableBytes();

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
     * return true,当且仅当{@code(this.capacity - this.writerIndex)}大于{@code 0}
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
     * 从NIO缓冲区，它设置{@code限制}为
     * 缓冲区的{@code容量}。
     */
    public abstract ByteBuf clear();

    /**
     * 获取缓冲区中指定的绝对{@code index}处的字节。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code索引}小于{@code 0}或
     *                                   {@code index + 1}大于{@code this.capacity}
     */
    public abstract byte getByte(int index);

    /**
     * 在指定的绝对{@code index}处获取一个32位整数
     * 这个缓冲区。此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code索引}小于{@code 0}或
     *                                   {@code index + 1}大于{@code this.capacity}
     */
    public abstract int getInt(int index);

    /**
     * 从指定的绝对{@code index}开始将该缓冲区的数据传输到指定的目的地，
     * 直到目的地变为不可写。这个方法基本上与{@link #getBytes(int, ByteBuf, int, int)}相同，
     * 只是这个方法增加目标的{@code writerIndex}，而{@link #getBytes(int, ByteBuf, int, int)}没有增加。
     * 此方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @throws IndexOutOfBoundsException 如果指定的{@code索引}小于{@code 0}或
     *                                   如果{@code index + dst。writableBytes}大于
     *                                   {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst);

    /**
     * 从指定的绝对{@code index}开始将该缓冲区的数据传输到指定的目的地，
     * 直到目的地变为不可写。这个方法基本上与{@link #getBytes(int, ByteBuf, int, int)}相同，
     * 只是这个方法增加目标的{@code writerIndex}，而{@link #getBytes(int, ByteBuf, int, int)}没有增加。
     * 此方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @param length 要传输的字节数
     * @throws IndexOutOfBoundsException 如果指定的{@code index}小于{@code 0}，
     *                                   如果{@code index + length}大于
     *                                   {@code这个。或者如果{@code length}大于{@code dst.writableBytes}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int length);

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
     * 将缓冲区的数据传输到指定的目的地
     * 指定的绝对{@code index}。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}
     *
     * @throws IndexOutOfBoundsException
     */
    public abstract ByteBuf getBytes(int index, byte[] dst);

    /**
     * 从指定的绝对{@code index}开始，将该缓冲区的数据传输到指定的目的地。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param dstIndex 目的地的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException
     */
    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * 从指定的绝对{@code index}开始将该缓冲区的数据传输到指定的目的地，直到目的地的位置达到其限制。
     * 当目标的{@code position}增加时，此方法不会修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
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
     * 在缓冲区中指定的绝对{@code index}处设置指定的字节。指定值的24个高阶位被忽略。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setByte(int index, int value);

    /**
     * 在缓冲区中指定的绝对{@code index}处设置指定的32位整数。
     * 此方法不会修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setInt(int index, int value);


    /**
     * 从指定的绝对{@code index}开始将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区变为不可读。
     * 这个方法与{@link #setBytes(int, ByteBuf, int, int)}基本相同，除了这个方法增加源缓冲区的{@code readerIndex}的传输字节数，
     * 而{@link #setBytes(int, ByteBuf, int, int)}没有。
     * 此方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   if {@code index + src.readableBytes} is greater than
     *                                   {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src);

    /**
     * 从指定的绝对{@code index}开始将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区变为不可读。
     * 这个方法与{@link #setBytes(int, ByteBuf, int, int)}基本相同，除了这个方法增加源缓冲区的{@code readerIndex}的传输字节数，
     * 而{@link #setBytes(int, ByteBuf, int, int)}没有。
     * 此方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @param length 要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0},
     *                                   if {@code index + length} is greater than
     *                                   {@code this.capacity}, or
     *                                   if {@code length} is greater than {@code src.readableBytes}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int length);

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
     * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or
     *                                   if {@code index + src.length} is greater than
     *                                   {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, byte[] src);

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
     * 在当前{@code readerIndex}处获取一个32位整数。
     * 并在该缓冲区中增加{@code readerIndex}的{@code 4}。
     *
     * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract int readInt();


    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，直到目的地变为
     * 不可写，并将{@code readerIndex}增加传输的字节数。这个方法与{@link #readBytes(ByteBuf, int, int)}基本相同，
     * 除了这个方法增加目标的{@code writerIndex}的传输字节数，而{@link #readBytes(ByteBuf, int, int)}没有。
     *
     * @throws IndexOutOfBoundsException if {@code dst.writableBytes} is greater than
     *                                   {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst);

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，并将{@code readerIndex}增加传输的字节数(= {@code length})。
     * 这个方法与{@link #readBytes(ByteBuf, int, int)}基本相同，除了这个方法增加目标的{@code writerIndex}的传输字节数(= {@code length})，而{@link #readBytes(ByteBuf, int, int)}没有。
     *
     * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes} or
     *                                   if {@code length} is greater than {@code dst.writableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int length);

    /**
     * 从当前{@code readerIndex}开始，将该缓冲区的数据传输到指定的目的地，并将{@code readerIndex}增加传输的字节数(= {@code length})。
     *
     * @param dstIndex 目的地的第一个索引
     * @param length   要传输的字节数
     * @throws IndexOutOfBoundsException if the specified {@code dstIndex} is less than {@code 0},
     *                                   if {@code length} is greater than {@code this.readableBytes}, or
     *                                   if {@code dstIndex + length} is greater than
     *                                   {@code dst.capacity}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

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
     * 在当前{@code writerIndex}处设置指定的字节，并在此缓冲区中将{@code writerIndex}的值增加{@code 1}。
     * 指定值的24个高阶位被忽略。
     *
     * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 1}
     */
    public abstract ByteBuf writeByte(int value);


    /**
     * 在当前{@code writerIndex}处设置指定的32位整数，并在此缓冲区中将{@code writerIndex}的值增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 4}
     */
    public abstract ByteBuf writeInt(int value);

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
     * 从当前{@code writerIndex}开始，将指定的源缓冲区的数据传输到这个缓冲区，直到源缓冲区变得不可读，并将{@code writerIndex}增加传输的字节数。
     * 这个方法与{@link #writeBytes(ByteBuf, int, int)}基本相同，除了这个方法增加源缓冲区的{@code readerIndex}的传输字节数，
     * 而{@link #writeBytes(ByteBuf, int, int)}没有。
     *
     * @param length 要传输的字节数
     * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.writableBytes} or
     *                                   if {@code length} is greater then {@code src.readableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int length);

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
     * 定位指定的{@code值}的第一次出现
     * 缓冲区。从指定的{@code frommindex}中进行搜索
     * (包括)到指定的{@code toIndex} (exclusive)。
     * <p>
     * 如果{@code frommindex}大于{@code toIndex}，则搜索是
     * 按相反的顺序执行。
     * <p>
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @return 如果找到第一个事件的绝对索引。
     * {@code -1} otherwise.
     */
    public abstract int indexOf(int fromIndex, int toIndex, byte value);

    /**
     * 定位指定的{@code值}的第一次出现
     * 缓冲区。从当前{@code readerIndex}中进行搜索
     * (包括)到当前的{@code writerIndex} (exclusive)。
     * <p>
     * 这个方法不会修改的{@code readerIndex}或{@code writerIndex}
     * 这个缓冲区。
     * <p>
     * {@code readerIndex}
     * 和第一个出现，如果找到。{@code 1}。
     */
    public abstract int bytesBefore(byte value);

    /**
     * <p>
     * 定位指定的{@code值}的第一次出现
     * 缓冲区。搜索从当前的{@code readerIndex}开始
     * (包括)并持续到指定的{@code length}。
     * <p>
     * 这个方法不会修改的{@code readerIndex}或{@code writerIndex}
     * 这个缓冲区。
     * <p>
     * {@code readerIndex}
     * 和第一个出现，如果找到。{@code 1}。
     *
     * @throws IndexOutOfBoundsException if {@code length}大于{@code this.readableBytes}
     */
    public abstract int bytesBefore(int length, byte value);

    /**
     * 定位指定的{@code值}的第一次出现
     * 缓冲区。搜索从指定的{@code index}(含)开始
     * 和持续指定的{@code长度}。
     * <p>
     * 此方法不修改{@code readerIndex}或{@code writerIndex}的
     * 这个缓冲区。
     *
     * @return 指定的{@code index}和第一个出现项之间的字节数。{@code 1}。
     * @throws IndexOutOfBoundsException if {@code index + length} is greater than {@code this.capacity}
     */
    public abstract int bytesBefore(int index, int length, byte value);

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
     * @return {@code -1}如果这个缓冲区没有底层的{@link ByteBuffer}。
     * 如果该缓冲区至少有一个底层的{@link ByteBuffer}，则该底层的{@link ByteBuffer}的数目
     * {@link ByteBuffer}。注意，为了避免混淆，此方法不返回{@code 0}。
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     * <p>
     * 返回包含该缓冲区的NIO {@link ByteBuffer}的最大数目。注意{@link #nioBuffers()}
     * 或者{@link #nioBuffers(int, int)}可能返回更少的{@link ByteBuffer}。
     * @see # nioBuffer ()
     * @see # nioBuffer (int, int)
     * @see # nioBuffers ()
     * @see # nioBuffers (int, int)
     */
    public abstract int nioBufferCount();


    /**
     * 获取该缓冲区对应的nio缓存区，如果不存在将会调用 nioBuffer()赋值。
     * 当执行 nioBuffer() 时，这个缓冲区会被新的覆盖
     *
     * @return
     */
    public abstract ByteBuffer getNioBuffer();


    /**
     * 将该缓冲区的可读字节暴露为NIO {@link ByteBuffer}。返回的缓冲区
     * 与此缓冲区共享内容，同时更改返回的位置和限制
     * NIO缓冲区不影响该缓冲区的索引和标记。这个方法是一样的
     *
     * @throws UnsupportedOperationException if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     * @code buf.nioBuffer(buf.readerIndex ()， buf.readableBytes())}。这个方法没有
     * 修改该缓冲区的{@code readerIndex}或{@code writerIndex}。请注意
     * 如果该缓冲区是动态的，则返回的NIO缓冲区将看不到该缓冲区的更改
     * 缓冲，并调整其容量。
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract ByteBuffer nioBuffer();

    /**
     * 将该缓冲区的子区域暴露为NIO {@link ByteBuffer}。返回的缓冲区与该缓冲区共享内容，而改变返回的NIO缓冲区的位置和限制不影响该缓冲区的索引和标记。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态的，并且它调整了容量，那么返回的NIO缓冲区将看不到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract ByteBuffer nioBuffer(int index, int length);

    /**
     * 将该缓冲区的可读字节暴露为NIO {@link ByteBuffer}。
     * 返回的缓冲区与该缓冲区共享内容，而改变返回的NIO缓冲区的位置和限制不影响该缓冲区的索引和标记。
     * 此方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态的，并且它调整了容量，那么返回的NIO缓冲区将看不到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers();

    /**
     * 将该缓冲区的字节作为NIO {@link ByteBuffer}的指定索引和长度
     * 当改变位置和限制时，返回的缓冲区与该缓冲区共享内容
     * 返回的NIO缓冲区的*不影响该缓冲区的索引和标记。这种方法是否
     * 不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。请注意
     * 如果这个缓冲区是动态的，返回的NIO缓冲区将看不到这个缓冲区的变化
     * 缓冲器和它调整其容量。
     *
     * @throws UnsupportedOperationException if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers(int index, int length);


    /**
     * 当且仅当该缓冲区有指向后备数据的底层内存地址的引用时返回{@code true}。
     */
    public abstract boolean hasMemoryAddress();

    /**
     * 返回指向支持数据的第一个字节的低级内存地址。
     *
     * @throws UnsupportedOperationException if this buffer does not support accessing the low-level memory address
     */
    public abstract long memoryAddress();

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

    @Override
    public abstract ByteBuf touch();

    @Override
    public abstract ByteBuf touch(Object hint);
}
