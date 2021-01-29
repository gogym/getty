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

import com.gettyio.core.function.Function;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonReadBlockQueue;
import com.gettyio.core.util.LinkedQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;


/**
 * aio模式下控制数据输出
 *
 * @author gogym
 * @version 1.0.0
 * @className AioBufferWriter.java
 * @description
 * @date 2020/4/8
 */
public final class AioBufferWriter extends AbstractBufferWriter<ByteBuffer> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioBufferWriter.class);

    /**
     * 函数
     */
    private final Function<AioBufferWriter, Void> function;
    /**
     * 数据缓冲队列
     */
    private final LinkedQueue<ByteBuffer> queue;


    /**
     * 构造方法
     *
     * @param chunkPool             内存池
     * @param flushFunction         函数
     * @param bufferWriterQueueSize 写队列大小
     * @param chunkPoolBlockTime    内存池最大阻塞时间
     */
    public AioBufferWriter(ChunkPool chunkPool, Function<AioBufferWriter, Void> flushFunction, int bufferWriterQueueSize, int chunkPoolBlockTime) {
        this.chunkPool = chunkPool;
        this.chunkPoolBlockTime = chunkPoolBlockTime;
        this.function = flushFunction;
        queue = new LinkedNonReadBlockQueue<>(bufferWriterQueueSize);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            IOException ioException = new IOException("OutputStream has closed");
            LOGGER.error(ioException.getMessage(), ioException);
            throw ioException;
        }
        if (len <= 0 || b.length == 0) {
            return;
        }
        try {
            //申请写缓冲
            ByteBuffer chunkPage = chunkPool.allocate(len - off, chunkPoolBlockTime);
            int minSize = chunkPage.remaining();
            if (minSize == 0) {
                chunkPool.deallocate(chunkPage);
                throw new RuntimeException("ByteBuffer remaining is 0");
            }
            //写入数据
            chunkPage.put(b, off, b.length);
            chunkPage.flip();
            //已经读取完，写到缓冲队列
            while (queue.getCount() < queue.getCapacity()) {
                queue.put(chunkPage);
                break;
            }

        } catch (InterruptedException e) {
            LOGGER.error(e);
        } catch (TimeoutException e) {
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
    public void flush() throws IOException{
        if (closed) {
            throw new IOException("outputStream was closed");
        }
        //如果队列里有数据在调用，减少无意义的刷新
        if (queue.getCount() > 0) {
            function.apply(this);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("outputStream was closed");
        }
        //关闭前先把缓冲队列是数据输出完
        while (queue.getCount() > 0) {
            flush();
        }

        closed = true;

        if (chunkPool != null) {
            //清空内存池
            chunkPool.clear();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public ByteBuffer poll() {
        try {
            return queue.poll();
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }


}