package com.gettyio.core.buffer.allocator;


import com.gettyio.core.buffer.buffer.ByteBuf;

/**
 * 实现负责分配缓冲区。这个接口的实现应该是线程安全的。
 */
public interface ByteBufAllocator {

    /**
     * 分配一个{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer();

    /**
     * 分配一个具有给定初始容量的{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 使用给定的初始容量和最大容量分配一个{@link ByteBuf}。是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer();

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer(int initialCapacity);

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个堆{@link ByteBuf}。
     */
    ByteBuf heapBuffer();

    /**
     * 分配一个具有给定初始容量的堆{@link ByteBuf}。
     */
    ByteBuf heapBuffer(int initialCapacity);

    /**
     * 使用给定的初始容量和最大容量分配一个堆{@link ByteBuf}。
     */
    ByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个堆外缓冲区{@link ByteBuf}。
     */
    ByteBuf directBuffer();

    /**
     * 使用给定的初始容量分配一个直接的{@link ByteBuf}。
     */
    ByteBuf directBuffer(int initialCapacity);

    /**
     * 使用给定的初始容量和最大容量分配一个直接的{@link ByteBuf}。
     */
    ByteBuf directBuffer(int initialCapacity, int maxCapacity);


    /**
     * 如果直接的{@link ByteBuf}被池化，则返回{@code true}
     */
    boolean isDirectBufferPooled();

    /**
     * 当{@link ByteBuf}需要通过{@code minNewCapacity}以{@code maxCapacity}为上限进行扩展时，计算{@link ByteBuf}的新容量。
     */
    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
}
