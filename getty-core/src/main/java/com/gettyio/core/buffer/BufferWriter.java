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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 控制数据输出。
 * <p>
 * 内部使用 {@link ConcurrentLinkedQueue}（无界锁队列）缓存待发送的
 * byte[]。写入线程通过 CAS 追加后立即返回，永不阻塞业务线程；
 * 消费线程（EventLoop / AIO 写线程）通过 {@link #pollAllBytes(List)} 从队列批量取出数据写出。
 * </p>
 *
 * @author gogym
 */
public final class BufferWriter extends AbstractBufferWriter {

    /**
     * 写出通知器，用于通知 EventLoop 注册 OP_WRITE
     */
    private final FlushNotifier flushNotifier;

    /**
     * 无界锁队列，缓存待写出的 byte[]。
     * 业务线程入队，IO 线程（EventLoop / AIO 写线程）出队并分配 PooledByteBuffer。
     */
    private final ConcurrentLinkedQueue<byte[]> byteQueue = new ConcurrentLinkedQueue<>();

    /**
     * 构造方法
     *
     * @param flushNotifier 写出通知器
     */
    public BufferWriter(FlushNotifier flushNotifier) {
        this.flushNotifier = flushNotifier;
    }

    @Override
    public void writeAndFlush(byte[] bytes) throws IOException {
        write(bytes);
        flush();
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        if (closed) {
            throw new IOException("BufferWriter is closed");
        }
        if (bytes == null) {
            throw new NullPointerException("bytes is null");
        }
        byteQueue.offer(bytes);
    }

    /**
     * 通知 EventLoop 注册 OP_WRITE，由 EventLoop 驱动实际写出。
     * <p>业务线程调用后立即返回，不参与任何 I/O 操作。</p>
     */
    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("BufferWriter is closed");
        }
        flushNotifier.notifyFlush();
    }

    /**
     * 关闭输出流。
     * <p>
     * 标记为已关闭，清空队列中残留的缓冲区，防止资源泄漏。
     * </p>
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        byteQueue.clear();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void pollAllBytes(List<byte[]> list) {
        byte[] bytes;
        while ((bytes = byteQueue.poll()) != null) {
            list.add(bytes);
        }
    }

    /**
     * 获取队列中待写出的 byte[] 数量。
     *
     * @return 待写出数量
     */
    @Override
    public int getCount() {
        return byteQueue.size();
    }
}
