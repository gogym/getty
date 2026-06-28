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
package com.gettyio.core.channel;

import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.FlushNotifier;
import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.loop.AioWriteThread;
import com.gettyio.core.channel.loop.AioWriteThreadGroup;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.WritePendingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AIO（异步 I/O）通道实现。
 * <p>
 * 基于 {@link AsynchronousSocketChannel}，通过 CompletionHandler 回调机制
 * 实现非阻塞的读写操作。写出采用 BufferWriter 链表缓存 + Gathering Write 设计：
 * 业务线程将数据追加到链表后立即返回，由共享写线程 {@link AioWriteThread} 驱动，
 * 通过 {@code channel.write(ByteBuffer[])} 批量写出所有缓冲区，
 * 通过回调 {@link #writeCompleted()} 唤醒写线程驱动后续写出。
 * 写线程无数据时 {@code park} 阻塞，避免空转。
 * </p>
 *
 * @author gogym
 */
public class AioChannel extends AbstractSocketChannel implements FlushNotifier {

    /**
     * 底层异步 Socket 通道
     */
    private final AsynchronousSocketChannel channel;

    /**
     * 读缓冲区（每次读取时从池中获取，读完释放）
     */
    private PooledByteBuffer readByteBuffer;

    /**
     * 数据输出组件（单向链表缓存待写出缓冲区）
     */
    private final BufferWriter bufferWriter;

    /**
     * 标记 AIO 异步写是否进行中。
     * <p>
     * CAS 保证全局只有一个执行流进入 drain + submit 路径。
     * false = 可提交新写操作，true = 上一次 write 尚未完成。
     * </p>
     */
    private final AtomicBoolean writeInFlight = new AtomicBoolean(false);

    /**
     * 共享写线程。多个 Channel 共享同一写线程，通过 wakeup 唤醒。
     */
    private final AioWriteThread writeThread;

    /**
     * 写线程组。用于重连等场景复用同一线程组。
     */
    private final AioWriteThreadGroup writeThreadGroup;

    /**
     * PooledByteBuffer 列表，由写线程构建。
     * 业务线程在 writeToSocket 中分配 PooledByteBuffer，AIO 回调线程释放时走 MPSC 队列回到业务线程缓存。
     * tryDrainAndSubmit / writeCompleted 在 writeInFlight=true 时访问，无并发。
     */
    private final List<PooledByteBuffer> drainBufs = new ArrayList<>();

    /**
     * Gathering Write 的 ByteBuffer 视图数组，预分配复用。
     * 仅在 writeInFlight=true 时由写线程或 AIO 回调线程访问，无并发。
     * 不够大时双倍扩容，避免每次 submitWrite 都 new 数组。
     */
    private ByteBuffer[] writeViews = new ByteBuffer[16];

    /**
     * 读完成回调
     */
    private final ReadCompletionHandler readCompletionHandler;

    /**
     * Gathering Write 完成回调
     */
    private final WriteCompletionHandler writeCompletionHandler = new WriteCompletionHandler();

    /**
     * SSL 处理器
     */
    private SSLHandler sslHandler;

    /**
     * SSL 握手监听器
     */
    private IHandshakeListener handshakeListener;

    /**
     * 构造 AIO 通道。
     *
     * @param channel               底层异步通道
     * @param config                通道配置
     * @param readCompletionHandler 读回调处理器
     * @param byteBufferPool        内存池
     * @param channelInitializer    管道初始化器
     */
    public AioChannel(AsynchronousSocketChannel channel, GettyConfig config,
                      ReadCompletionHandler readCompletionHandler,
                      ByteBufferPool byteBufferPool, ChannelInitializer channelInitializer,
                      AioWriteThread writeThread, AioWriteThreadGroup writeThreadGroup) {
        this.channel = channel;
        this.readCompletionHandler = readCompletionHandler;
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.channelInitializer = channelInitializer;
        this.writeThread = writeThread;
        this.writeThreadGroup = writeThreadGroup;
        this.bufferWriter = new BufferWriter(this);

        // 注册到共享写线程
        writeThread.register(this);

        try {
            channelInitializer.initChannel(this);
        } catch (Exception e) {
            try {
                channel.close();
            } catch (IOException ex) { /* ignore */ }
            throw new RuntimeException("channelPipeline init exception", e);
        }

        // 触发新连接事件
        try {
            invokePipeline(ChannelState.NEW_CHANNEL, null);
        } catch (Exception e) {
            logger.error("fire NEW_CHANNEL failed", e);
        }
    }

    // ==================== 读操作 ====================

    @Override
    public void starRead() {
        initiateClose = false;
        continueRead();
        if (sslHandler != null) {
            sslHandler.beginHandshake();
        }
    }

    /**
     * 发起下一次异步读取。从内存池获取缓冲区并提交给通道。
     */
    private void continueRead() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        readByteBuffer = byteBufferPool.acquire(config.getReadBufferSize());
        channel.read(readByteBuffer.flipToFill(), this, readCompletionHandler);
    }

    /**
     * 处理异步读完成事件。将缓冲区数据输送到管道，然后释放缓冲区并发起下一次读取。
     *
     * @param eof 是否已到达流末尾（read 返回 -1）
     */
    public void readFromChannel(boolean eof) {
        // 立即置空字段引用，防止 close() 并发调用导致双重释放
        PooledByteBuffer readBuf = this.readByteBuffer;
        this.readByteBuffer = null;
        if (readBuf == null) {
            return;
        }

        // 切换到读模式：writerIndex 从 ByteBuffer.position 同步，readerIndex = 0
        readBuf.flipToFlush();

        if (readBuf.isReadable()) {
            try {
                invokePipeline(ChannelState.CHANNEL_READ, readBuf);
            } catch (Exception e) {
                logger.error("pipeline read handler error", e);
                readBuf.release();
                try {
                    invokePipeline(ChannelState.CHANNEL_EXCEPTION, null);
                } catch (Exception ex) {
                    logger.error("invoke CHANNEL_EXCEPTION failed", ex);
                }
                close();
                return;
            }
        }

        if (eof) {
            readBuf.release();
            close();
            return;
        }

        readBuf.release();
        continueRead();
    }

    // ==================== 写操作 ====================

    /**
     * 管道处理后触发写出。
     * <p>
     * 数据已由 {@link #writeToSocket(Object)} 追加到 BufferWriter 链表，
     * </p>
     */
    @Override
    public boolean writeAndFlush(Object obj) {
        try {
            write(obj);
            flush();
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        return true;
    }

    /**
     * 经过责任链编码后追加到 BufferWriter 链表，不触发实际写出。
     * <p>
     * 数据经管道编码后到达 {@link #writeToSocket(Object)}，
     * 仅追加到 BufferWriter 链表，需配合 {@link #flush()} 使用才能实际发出。
     * </p>
     */
    @Override
    public void write(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error("write failed", e);
        }
    }

    /**
     * 刷新写缓冲区，触发实际写出。
     * <p>
     * 调用 {@link #notifyFlush()} 从 BufferWriter 链表取数据并提交给 AIO 通道写出。
     * </p>
     */
    @Override
    public void flush() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        notifyFlush();
    }

    /**
     * 管道终点：将消息入队，由写线程拉取并提交 AIO 写出。
     * <p>
     * 仅接受 {@link PooledByteBuffer} 类型。编码器须直接输出 PooledByteBuffer。
     * </p>
     */
    @Override
    public void writeToSocket(Object msg) {
        try {
            if (msg instanceof PooledByteBuffer) {
                PooledByteBuffer buf = (PooledByteBuffer) msg;
                if (!buf.isReadable()) {
                    buf.release();
                    return;
                }
                bufferWriter.write(buf);
            }
        } catch (Exception e) {
            logger.error("writeToSocket failed", e);
        }
    }

    /**
     * FlushNotifier 实现：唤醒写线程提交写出。
     * <p>
     * 业务线程仅通过 wakeup 通知写线程，不直接执行任何 I/O 操作。
     * </p>
     */
    @Override
    public void notifyFlush() {
        writeThread.wakeup();
    }


    /**
     * 使用 Gathering Write 提交异步写出。
     * <p>
     * 从 PooledByteBuffer 获取底层 ByteBuffer，设置 position/limit 为可读范围。
     * AIO 写出会推进 ByteBuffer 的 position，{@link #writeCompleted()} 通过
     * {@code readerIndex} 同步回 position，支持部分写出追踪。
     * </p>
     *
     * @param bufs 待写出的 PooledByteBuffer 列表（写线程分配）
     */
    private void submitWrite(List<PooledByteBuffer> bufs) {

        if (bufs.isEmpty()) {
            // 无数据可写，释放写权限
            writeInFlight.set(false);
            return;
        }

        int size = bufs.size();
        // 复用 writeViews 数组，不够大时双倍扩容
        if (writeViews.length < size) {
            writeViews = new ByteBuffer[Integer.highestOneBit(size) << 1];
        }
        for (int i = 0; i < size; i++) {
            PooledByteBuffer buf = bufs.get(i);
            ByteBuffer bb = buf.getBuffer();
            bb.position(buf.readerIndex());
            bb.limit(buf.writerIndex());
            writeViews[i] = bb;
        }
        try {
            channel.write(writeViews, 0, size, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
        } catch (Exception e) {
            logger.error("channel write failed", e);
            // 写出失败，释放所有缓冲区
            for (PooledByteBuffer buf : bufs) {
                buf.release();
            }
            writeInFlight.set(false);
            close();
        }
    }

    /**
     * 写出完成回调。由 AIO 回调线程调用，在持有 writeInFlight 的状态下
     * 完成全部后续处理，避免先释放锁再重新获取的窗口问题。
     * <p>
     * 处理流程：
     * 1. 同步 readerIndex，释放已写完的缓冲区（release 走 MPSC 队列回到业务线程缓存）
     * 2. 部分写出 → 直接重新提交（writeInFlight 保持 true）
     * 3. 全部写完 → 在持有锁的状态下检查 BufferWriter 队列
     * 4. 队列空 → 释放锁，再检查一次防止竞争
     * </p>
     */
    public void writeCompleted() {
        try {
            // 1. 同步 readerIndex，释放已写完的缓冲区
            int firstRemaining = -1;
            for (int i = 0; i < drainBufs.size(); i++) {
                PooledByteBuffer buf = drainBufs.get(i);
                ByteBuffer bb = buf.getBuffer();
                buf.readerIndex(bb.position());
                if (buf.isReadable()) {
                    firstRemaining = i;
                    break;
                }
                // 已完全写出，释放（走 MPSC 队列回到业务线程缓存）
                buf.release();
            }

            if (firstRemaining >= 0) {
                // 2. 部分写出 → 一次性前移 + 尾部截断，O(n) 完成
                int remaining = drainBufs.size() - firstRemaining;
                for (int j = 0; j < remaining; j++) {
                    drainBufs.set(j, drainBufs.get(firstRemaining + j));
                }
                while (drainBufs.size() > remaining) {
                    drainBufs.remove(drainBufs.size() - 1);
                }
                submitWrite(drainBufs);
                return;
            }

            // 3. 全部写完，释放写权限
            drainBufs.clear();
            writeInFlight.set(false);
        } finally {
            writeThread.wakeup();
        }
    }

    /**
     * writeCompleted 异常时的清理操作。
     * <p>
     * 由 {@link com.gettyio.core.channel.internal.WriteCompletionHandler} 在
     * writeCompleted 抛出异常时调用。重置 writeInFlight 并唤醒写线程重新 drain。
     * </p>
     */
    public void writeCompletedFailed() {
        writeInFlight.set(false);
        writeThread.wakeup();
    }

    /**
     * 尝试 drain 并提交 AIO 写出。由共享写线程遍历调用。
     * <p>
     * CAS 保证同一 Channel 只有一个执行流进入 drain + submit 路径。
     * 如果 AIO 正在写出（writeInFlight=true），则跳过，等待 AIO 回调唤醒写线程。
     * </p>
     */
    public void tryDrainAndSubmit() {
        if (writeInFlight.compareAndSet(false, true)) {
            drainBufs.clear();
            bufferWriter.pollAll(drainBufs);
            if (drainBufs.isEmpty()) {
                // 无数据，释放写权限
                writeInFlight.set(false);
            } else {
                submitWrite(drainBufs);
            }
        }
    }

    /**
     * 获取本 Channel 所属的写线程。
     */
    public AioWriteThread getWriteThread() {
        return writeThread;
    }

    /**
     * 获取写线程组。用于重连等场景复用同一线程组。
     */
    public AioWriteThreadGroup getWriteThreadGroup() {
        return writeThreadGroup;
    }

    // ==================== 关闭 ====================

    @Override
    public synchronized void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        status = CHANNEL_STATUS_CLOSED;

        PooledByteBuffer rBuf = readByteBuffer;
        if (rBuf != null) {
            readByteBuffer = null;
            rBuf.release();
        }

        // 从共享写线程注销
        writeThread.unregister(this);

        // 释放 drainBufs 中残留的 PooledByteBuffer
        for (PooledByteBuffer buf : drainBufs) {
            buf.release();
        }
        drainBufs.clear();

        try {
            bufferWriter.close();
        } catch (Exception e) {
            logger.error("close bufferWriter failed", e);
        }

        fireChannelFutureListeners();

        try {
            channel.shutdownInput();
        } catch (IOException e) {
            logger.error("shutdownInput failed", e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            logger.error("shutdownOutput failed", e);
        }
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("close channel failed", e);
        }

        try {
            invokePipeline(ChannelState.CHANNEL_CLOSED, null);
        } catch (Exception e) {
            logger.error("fire CHANNEL_CLOSED failed", e);
        }
    }

    @Override
    public synchronized void close(boolean initiateClose) {
        this.initiateClose = initiateClose;
        close();
    }

    // ==================== 地址与通道信息 ====================

    @Override
    public final InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getLocalAddress();
    }

    @Override
    public final InetSocketAddress getRemoteAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || channel == null) {
            throw new IOException("channel is closed");
        }
    }

    // ==================== SSL ====================

    @Override
    public void setSslHandler(SSLHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    @Override
    public SSLHandler getSslHandler() {
        return sslHandler;
    }

    @Override
    public void setSslHandshakeListener(IHandshakeListener handshakeListener) {
        this.handshakeListener = handshakeListener;
    }

    @Override
    public IHandshakeListener getSslHandshakeListener() {
        return handshakeListener;
    }

}
