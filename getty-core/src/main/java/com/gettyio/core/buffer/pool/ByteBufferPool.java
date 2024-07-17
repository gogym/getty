package com.gettyio.core.buffer.pool;


/**
 * ByteBufferPool接口定义了一个ByteBuffer的池化机制。
 * 通过这个接口，可以获取到一定大小的ByteBuffer实例，用于减少频繁创建和销毁ByteBuffer对象带来的性能开销。
 */
public interface ByteBufferPool {

    /**
     * 从池中获取一个ByteBuffer实例。
     *
     * @param size 需要获取的ByteBuffer的大小。这个大小是指ByteBuffer能够存储的数据的字节长度。
     * @param direct 指定是否需要一个直接的ByteBuffer。直接的ByteBuffer会在Java堆外分配内存，可能会提高某些情况下的性能。
     * @return 返回一个RetainableByteBuffer实例，这个实例可以从池中获取并使用，用完后需要释放以返回池中。
     */
    RetainableByteBuffer acquire(int size, boolean direct);

    /**
     * 申请一个缓冲区
     *
     * @param size
     * @return
     */
    RetainableByteBuffer acquire(int size);


}

