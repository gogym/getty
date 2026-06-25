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
package com.gettyio.core.buffer;

import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import java.util.function.Function;
import com.gettyio.core.util.queue.LinkedBlockQueue;
import com.gettyio.core.util.queue.LinkedQueue;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 控制数据输出。
 * <p>
 * 将写入的字节数据封装为池化缓冲区，排入队列等待 flush 写出。
 * </p>
 *
 * @author gogym
 */
public final class BufferWriter extends AbstractBufferWriter {

    /**
     * flush 回调函数
     */
    private final Function<BufferWriter, Void> function;

    /**
     * 数据缓冲队列
     */
    private final LinkedQueue<RetainableByteBuffer> queue;

    /**
     * 缓冲区池
     */
    private final ByteBufferPool byteBufferPool;

    /**
     * 构造方法
     *
     * @param byteBufferPool        内存池
     * @param flushFunction         flush 回调
     * @param bufferWriterQueueSize 写队列大小
     */
    public BufferWriter(ByteBufferPool byteBufferPool, Function<BufferWriter, Void> flushFunction, int bufferWriterQueueSize) {
        this.byteBufferPool = byteBufferPool;
        this.function = flushFunction;
        this.queue = new LinkedBlockQueue<>(bufferWriterQueueSize);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("BufferWriter is closed");
        }
        if (b == null) {
            throw new NullPointerException("byte array is null");
        }
        if (len <= 0) {
            return;
        }
        // 从池中获取精确大小的缓冲区
        RetainableByteBuffer byteBuf = byteBufferPool.acquire(len);
        // 直接通过 arraycopy 写入指定范围数据，避免经过 BufferUtil 多层调用
        ByteBuffer buf = byteBuf.getBuffer();
        int pos = buf.position();
        int limit = buf.limit();
        if (pos == limit) {
            buf.position(0);
            buf.limit(buf.capacity());
        } else {
            int capacity = buf.capacity();
            if (limit == capacity) {
                buf.compact();
            } else {
                buf.position(limit);
                buf.limit(capacity);
            }
            pos = limit;
        }
        // 直接 arraycopy，比 ByteBuffer.put() 更高效（堆内存场景）
        if (buf.hasArray()) {
            System.arraycopy(b, off, buf.array(), buf.arrayOffset() + buf.position(), len);
            buf.position(buf.position() + len);
        } else {
            buf.put(b, off, len);
        }
        // 切回 flush 模式
        buf.limit(buf.position());
        buf.position(pos);

        try {
            queue.put(byteBuf);
        } catch (InterruptedException e) {
            byteBuf.release();
            Thread.currentThread().interrupt();
            throw new IOException("Write interrupted", e);
        }
    }

    @Override
    public void writeAndFlush(byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException("byte array is null");
        }
        write(b, 0, b.length);
        flush();
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("BufferWriter is closed");
        }
        function.apply(this);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("BufferWriter is closed");
        }
        flush();
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public RetainableByteBuffer poll() {
        try {
            return queue.poll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public int getCount() {
        return queue.getCount();
    }
}
