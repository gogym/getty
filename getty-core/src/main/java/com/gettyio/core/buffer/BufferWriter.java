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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.util.LinkedBufferList;

import java.io.IOException;
import java.util.List;

/**
 * 控制数据输出。
 * <p>
 * 内部使用 {@link LinkedBufferList}（Entry 单向链表）缓存待发送的
 * {@link PooledByteBuffer}。写入线程加锁追加后立即返回，永不阻塞业务线程；
 * 消费线程（EventLoop）通过 {@link #poll()} 从链表头部取出数据写出。
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
     * 单向链表，缓存待写出的缓冲区
     */
    private final LinkedBufferList bufferList = new LinkedBufferList();

    /**
     * 构造方法
     *
     * @param flushNotifier 写出通知器
     */
    public BufferWriter(FlushNotifier flushNotifier) {
        this.flushNotifier = flushNotifier;
    }

    /**
     * 零拷贝写入：将 PooledByteBuffer 追加到链表尾部并触发 flush。
     * <p>
     * 调用方必须确保传入的 PooledByteBuffer 生命周期由 BufferWriter 接管，
     * 即入队后不再使用此缓冲区。通道层在写出完成后会自动调用 release()。
     * </p>
     * <p>写入线程加锁追加后立即返回，永不阻塞。</p>
     *
     * @param byteBuf 待写出的缓冲区
     * @throws IOException 已关闭时抛出
     */
    @Override
    public void writeAndFlush(PooledByteBuffer byteBuf) throws IOException {
        write(byteBuf);
        flush();
    }

    @Override
    public void write(PooledByteBuffer byteBuf) throws IOException {
        if (closed) {
            byteBuf.release();
            throw new IOException("BufferWriter is closed");
        }
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf is null");
        }
        synchronized (this) {
            if (closed) {
                // 加锁后二次检查：防止 close() 在首次检查和入队之间完成
                byteBuf.release();
                return;
            }
            // 追加到链表尾部，O(1)
            bufferList.offer(byteBuf);
        }
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
     * 标记为已关闭，清空链表中残留的缓冲区，防止资源泄漏。
     * </p>
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        // 1. 先标记为已关闭，拒绝新的写入
        closed = true;
        // 2. 清空链表中残留的缓冲区，防止内存泄漏
        bufferList.drainAndRelease();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * 从链表头部弹出一个缓冲区。
     * <p>
     * 非阻塞：链表为空时立即返回 null。
     * </p>
     *
     * @return 缓冲区，链表为空时返回 null
     */
    @Override
    public PooledByteBuffer poll() {
        synchronized (this) {
            if (closed) {
                return null;
            }
            return bufferList.poll();
        }
    }

    /**
     * 从链表中批量弹出所有可用缓冲区。
     *
     * @param list 用于接收弹出元素的列表（调用方传入，避免每次分配）
     */
    @Override
    public void pollAll(List<PooledByteBuffer> list) {
        synchronized (this) {
            bufferList.pollAll(list);
        }
    }

    /**
     * 从链表中批量弹出最多 maxCount 个缓冲区。
     *
     * @param list     用于接收弹出元素的列表
     * @param maxCount 最多弹出数量
     */
    @Override
    public void pollAll(List<PooledByteBuffer> list, int maxCount) {
        synchronized (this) {
            bufferList.pollAll(list, maxCount);
        }
    }

    /**
     * 获取链表中待写出的缓冲区数量。
     *
     * @return 待写出数量
     */
    @Override
    public int getCount() {
        return bufferList.size();
    }
}
