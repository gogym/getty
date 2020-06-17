/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * BufferWriter.java
 *
 * @description:用于控制数据输出
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public final class NioBufferWriter extends BufferWriter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioBufferWriter.class);
    /**
     * 缓冲池
     */
    private final ChunkPool chunkPool;

    /**
     * 内存申请最大阻塞时间
     */
    private int chunkPoolBlockTime;
    /**
     * 当前是否已关闭
     */
    private boolean closed = false;
    /**
     * 阻塞队列
     */
    private final LinkedQueue<ChannelByteBuffer> queue;

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
            if (minSize == 0) {
                chunkPool.deallocate(chunkPage);
                throw new RuntimeException("ByteBuffer remaining is 0");
            }
            //写入数据
            chunkPage.put(b, off, b.length);
            //if (!chunkPage.hasRemaining()) {
            chunkPage.flip();
            //已经读取完，写到缓冲队列
            queue.put(new ChannelByteBuffer(socketChannel, chunkPage));
            // }

        } catch (InterruptedException e) {
            LOGGER.error(e);
        } catch (TimeoutException e) {
            LOGGER.error(e);
        }
    }


    /**
     * 刷新缓冲区。
     *
     * @param b 数组
     * @throws IOException 抛出异常
     */
    public void writeAndFlush(SocketChannel socketChannel, byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        writeAndFlush(socketChannel, b, 0, b.length);
    }

    /**
     * @param b   待输出数据
     * @param off 起始位点
     * @param len 输出的数据长度
     * @throws IOException 抛出异常
     */
    private void writeAndFlush(SocketChannel socketChannel, byte[] b, int off, int len) throws IOException {
        write(socketChannel, b, off, len);
        flush();
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

    public boolean isClosed() {
        return closed;
    }


    public ChannelByteBuffer poll() {
        try {
            return queue.poll();
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }


}