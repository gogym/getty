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
import com.gettyio.core.function.Function;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.queue.LinkedBlockQueue;
import com.gettyio.core.util.queue.LinkedQueue;

import java.io.IOException;


/**
 * 控制数据输出
 *
 * @author gogym
 * @version 1.0.0
 * @className BufferWriter.java
 * @description
 * @date 2020/4/8
 */
public final class BufferWriter extends AbstractBufferWriter<RetainableByteBuffer> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(BufferWriter.class);

    /**
     * 函数
     */
    private final Function<BufferWriter, Void> function;

    /**
     * 数据缓冲队列
     */
    private final LinkedQueue<RetainableByteBuffer> queue;

    /**
     * 缓冲区构造器
     */
    private final ByteBufferPool byteBufferPool;

    /**
     * 构造方法
     *
     * @param byteBufferPool      内存池
     * @param flushFunction         函数
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
            IOException ioException = new IOException("OutputStream is closed");
            LOGGER.error(ioException.getMessage(), ioException);
            throw ioException;
        }
        if (len <= 0 || b.length == 0) {
            return;
        }
        try {
            //申请写缓冲
            ByteBuf byteBuf = byteBufAllocator.buffer(len - off);
            //写入数据
            byteBuf.writeBytes(b);
            //写到缓冲队列
            queue.put(byteBuf);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    @Override
    public void writeAndFlush(byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        write(b, 0, b.length);
        flush();
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("outputStream is closed");
        }
        function.apply(this);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("outputStream is closed");
        }
        //关闭前先把缓冲队列是数据输出完
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
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public int getCount() {
        return queue.getCount();
    }
}