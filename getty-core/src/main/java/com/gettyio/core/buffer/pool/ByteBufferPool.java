package com.gettyio.core.buffer.pool;


public interface ByteBufferPool {


    /**
     * 申请一个缓冲区
     *
     * @param size
     * @param direct
     * @return
     */
    RetainableByteBuffer acquire(int size, boolean direct);


    RetainableByteBuffer acquire(int size);


}
