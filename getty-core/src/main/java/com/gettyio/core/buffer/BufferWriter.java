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

import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import java.util.function.Function;
import com.gettyio.core.util.queue.LinkedBlockQueue;
import com.gettyio.core.util.queue.LinkedQueue;

import java.io.IOException;

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
     * 构造方法
     *
     * @param flushFunction         flush 回调
     * @param bufferWriterQueueSize 写队列大小
     */
    public BufferWriter(Function<BufferWriter, Void> flushFunction, int bufferWriterQueueSize) {
        this.function = flushFunction;
        this.queue = new LinkedBlockQueue<>(bufferWriterQueueSize);
    }

    /**
     * 零拷贝写入：将 RetainableByteBuffer 直接入队，不进行任何数组拷贝。
     * <p>
     * 调用方必须确保传入的 RetainableByteBuffer 生命周期由 BufferWriter 接管，
     * 即入队后不再使用此缓冲区。通道层在写出完成后会自动调用 release()。
     * </p>
     *
     * @param byteBuf 待写出的缓冲区
     * @throws IOException 已关闭或入队中断时抛出
     */
    @Override
    public void writeAndFlush(RetainableByteBuffer byteBuf) throws IOException {
        if (closed) {
            byteBuf.release();
            throw new IOException("BufferWriter is closed");
        }
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf is null");
        }
        if (!byteBuf.isReadable()) {
            byteBuf.release();
            return;
        }
        // 同步 ByteBuffer 的 position/limit 到 readerIndex/writerIndex，确保通道可直接写出
        byteBuf.getBuffer().position(byteBuf.readerIndex());
        byteBuf.getBuffer().limit(byteBuf.writerIndex());
        try {
            queue.put(byteBuf);
        } catch (InterruptedException e) {
            // put 被中断（包括 drain 唤醒）：缓冲区未入队，由调用方释放
            byteBuf.release();
            Thread.currentThread().interrupt();
            throw new IOException("Write interrupted", e);
        }
        // 防止 put 阻塞期间 close() 已通过 drain 释放队列，确保不泄漏已入队的缓冲区
        if (closed) {
            byteBuf.release();
            throw new IOException("BufferWriter is closed");
        }
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
            return;
        }
        // 1. 先标记为已关闭，拒绝新的写入
        closed = true;
        // 2. 尽力刷新：让通道消费队列中的数据
        try {
            function.apply(this);
        } catch (Exception e) {
            // 通道可能已关闭，忽略刷新异常
        }
        // 3. 排空队列中残留的缓冲区，防止内存泄漏，同时唤醒可能阻塞的 put 线程
        queue.drain();
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
