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

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedBlockQueue;
import com.gettyio.core.util.LinkedQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;


/**
 * 用于控制nio数据输出
 * @className NioBufferWriter.java
 * @author gogym
 * @version 1.0.0
 * @description
 * @date 2020/4/8
 */
public final class NioBufferWriter extends AbstractBufferWriter<ChannelByteBuffer> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioBufferWriter.class);
    /**
     * 阻塞队列
     */
    private final LinkedQueue<ChannelByteBuffer> queue;


    /**
     * 构造函数
     * @param chunkPool 内存池
     * @param bufferWriterQueueSize 写出队列大小
     * @param chunkPoolBlockTime 内存池最大阻塞时间
     */
    public NioBufferWriter(ChunkPool chunkPool, int bufferWriterQueueSize, int chunkPoolBlockTime) {
        this.chunkPool = chunkPool;
        this.chunkPoolBlockTime = chunkPoolBlockTime;
        this.queue = new LinkedBlockQueue<>(bufferWriterQueueSize);
    }


    public void write(SocketChannel socketChannel, byte[] b, int off, int len) throws IOException {
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
            if (minSize <= 0) {
                chunkPool.deallocate(chunkPage);
                throw new RuntimeException("ByteBuffer remaining is 0");
            }
            //写入数据
            chunkPage.put(b, off, b.length);

            chunkPage.flip();
            //已经读取完，写到缓冲队列
            queue.put(new ChannelByteBuffer(socketChannel, chunkPage));

        } catch (InterruptedException e) {
            LOGGER.error(e);
        } catch (TimeoutException e) {
            LOGGER.error(e);
        }
    }


    /**
     * @param socketChannel
     * @param b   待输出数据
     * @throws IOException 抛出异常
     */
    public void writeAndFlush(SocketChannel socketChannel, byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        write(socketChannel, b, 0, b.length);
        //flush();
    }


    @Override
    public void flush() {
        if (closed) {
            throw new RuntimeException("OutputStream has closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("OutputStream has closed");
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
    public ChannelByteBuffer poll() {
        try {
            return queue.poll();
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }


}