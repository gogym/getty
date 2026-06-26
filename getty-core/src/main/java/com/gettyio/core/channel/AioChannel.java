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
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
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

/**
 * AIO（异步 I/O）通道实现。
 * <p>
 * 基于 {@link AsynchronousSocketChannel}，通过 CompletionHandler 回调机制
 * 实现非阻塞的读写操作。写出采用 BufferWriter 链表缓存 + Gathering Write 设计：
 * 业务线程将数据追加到链表后立即返回，由 {@link #drainPending()} 直接通过
 * {@code SocketChannel.write(ByteBuffer[])} 批量写出所有缓冲区，
 * 通过回调 {@link #writeCompleted()} 驱动后续写出。
 * </p>
 *
 * @author gogym
 */
public class AioChannel extends AbstractSocketChannel implements FlushNotifier {

    /** 底层异步 Socket 通道 */
    private final AsynchronousSocketChannel channel;

    /** 读缓冲区（每次读取时从池中获取，读完释放） */
    private PooledByteBuffer readByteBuffer;

    /** 数据输出组件（单向链表缓存待写出缓冲区） */
    private final BufferWriter bufferWriter;

    /**
     * 部分写出时暂存的缓冲区列表。
     * <p>
     * 当 Gathering Write 未一次性写完所有缓冲区时，
     * 剩余缓冲区保留在此列表中，由 {@link #writeCompleted()} 回调驱动下次写出。
     * </p>
     */
    private List<PooledByteBuffer> pendingWriteBufs;

    /**
     * 当前提交给 AIO 的 ByteBuffer 视图数组。
     * <p>
     * 写出完成后可通过 {@code position()} 获取每个缓冲区的实际写出量。
     * </p>
     */
    private ByteBuffer[] pendingWriteViews;

    /** 读完成回调 */
    private final ReadCompletionHandler readCompletionHandler;

    /** Gathering Write 完成回调 */
    private final WriteCompletionHandler writeCompletionHandler = new WriteCompletionHandler();

    /** SSL 处理器 */
    private SSLHandler sslHandler;

    /** SSL 握手监听器 */
    private IHandshakeListener handshakeListener;

    /**
     * 构造 AIO 通道。
     *
     * @param channel                底层异步通道
     * @param config                 通道配置
     * @param readCompletionHandler  读回调处理器
     * @param byteBufferPool         内存池
     * @param channelInitializer     管道初始化器
     */
    public AioChannel(AsynchronousSocketChannel channel, BaseConfig config,
                      ReadCompletionHandler readCompletionHandler,
                      ByteBufferPool byteBufferPool, ChannelInitializer channelInitializer) {
        this.channel = channel;
        this.readCompletionHandler = readCompletionHandler;
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.channelInitializer = channelInitializer;
        this.bufferWriter = new BufferWriter(this);

        try {
            channelInitializer.initChannel(this);
        } catch (Exception e) {
            try { channel.close(); } catch (IOException ex) { /* ignore */ }
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
        PooledByteBuffer readBuf = this.readByteBuffer;
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
                this.readByteBuffer = null;
                readBuf.release();
                try { invokePipeline(ChannelState.CHANNEL_EXCEPTION, null); } catch (Exception ex) { logger.error("invoke CHANNEL_EXCEPTION failed", ex); }
                close();
                return;
            }
        }

        if (eof) {
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
     * 数据已由 {@link #writeToChannel} 追加到 BufferWriter 链表，
     * 此处调用 {@link #drainPending()} 尝试提交写出。
     * </p>
     */
    @Override
    public boolean writeAndFlush(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        drainPending();
        return true;
    }

    /**
     * 将数据追加到 BufferWriter 链表。
     * <p>
     * 业务线程加锁追加后立即返回，永不阻塞。
     * 由 {@link BufferWriter#writeAndFlush} 内部触发 {@link #notifyFlush()}。
     * </p>
     */
    @Override
    public void writeToChannel(Object obj) {
        try {
            bufferWriter.writeAndFlush((PooledByteBuffer) obj);
        } catch (Exception e) {
            logger.error("writeToChannel failed", e);
        }
    }

    /**
     * FlushNotifier 实现：通知 AIO 提交写出。
     * <p>
     * AIO 无 OP_WRITE 概念，直接调用 {@link #drainPending()} 尝试提交异步写出。
     * 若上一次 write 尚未完成，{@code channel.write()} 会抛出
     * {@link WritePendingException}，数据留在 BufferWriter 链表中，
     * 由 {@link #writeCompleted()} 回调驱动下次写出。
     * </p>
     */
    @Override
    public void notifyFlush() {
        drainPending();
    }

    /**
     * 从 BufferWriter 链表取数据，使用 Gathering Write 批量写出。
     * <p>
     * 将链表中所有 {@link PooledByteBuffer} 通过 {@code asByteBuffer()} 创建视图，
     * 组成 {@code ByteBuffer[]} 数组，调用 {@code channel.write(ByteBuffer[])} 一次性写出。
     * 无需合并拷贝，零额外内存分配。
     * </p>
     * <p>
     * 部分写出处理：若 Socket 缓冲区满导致未一次性写完，
     * 通过推进 {@code readerIndex} 标记已写出量，剩余缓冲区保留在 {@link #pendingWriteBufs}，
     * 由 {@link #writeCompleted()} 回调驱动下次写出。
     * </p>
     */
    private void drainPending() {
        // 优先处理上次部分写出的剩余缓冲区
        List<PooledByteBuffer> bufs;
        if (pendingWriteBufs != null) {
            bufs = pendingWriteBufs;
            pendingWriteBufs = null;
        } else {
            bufs = new ArrayList<>();
            bufferWriter.pollAll(bufs);
            if (bufs.isEmpty()) {
                return;
            }
        }

        submitWrite(bufs);
    }

    /**
     * 使用 Gathering Write 提交异步写出。
     * <p>
     * 为每个 {@link PooledByteBuffer} 创建 {@link ByteBuffer} 视图，
     * 组成数组后调用 {@code channel.write(ByteBuffer[])}。
     * 若上一次 write 尚未完成，抛出 {@link WritePendingException}，
     * 缓冲区整体保留到 {@link #pendingWriteBufs}，由回调驱动下次写出。
     * </p>
     *
     * @param bufs 待写出的缓冲区列表
     */
    private void submitWrite(List<PooledByteBuffer> bufs) {
        // 为每个缓冲区创建 ByteBuffer 视图（不修改底层 ByteBuffer 的 position/limit）
        ByteBuffer[] views = new ByteBuffer[bufs.size()];
        for (int i = 0; i < bufs.size(); i++) {
            views[i] = bufs.get(i).asByteBuffer();
        }
        pendingWriteViews = views;

        try {
            channel.write(views, 0, views.length, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
        } catch (WritePendingException e) {
            // 上一次 write 尚未完成，缓冲区整体保留，由 writeCompleted() 驱动下次写出
            pendingWriteBufs = bufs;
            pendingWriteViews = null;
            logger.warn("WritePendingException in submitWrite", e);
        }
    }

    /**
     * 写出完成回调。处理部分写出，释放已写完的缓冲区，并继续写出。
     * <p>
     * 通过比较每个 {@link ByteBuffer} 视图的 {@code position} 变化量，
     * 精确计算每个缓冲区的已写出字节数，推进对应的 {@code readerIndex}。
     * 全部写完则释放所有缓冲区；部分写出则保留剩余缓冲区供下次写出。
     * </p>
     */
    public void writeCompleted() {
        ByteBuffer[] views = pendingWriteViews;
        List<PooledByteBuffer> bufs = pendingWriteBufs;
        pendingWriteViews = null;
        pendingWriteBufs = null;

        if (views != null && bufs != null) {
            // 通过 ByteBuffer 视图的 position 变化量计算每个缓冲区的实际写出量
            List<PooledByteBuffer> remaining = null;

            for (int i = 0; i < bufs.size(); i++) {
                PooledByteBuffer buf = bufs.get(i);
                int written = views[i].position();
                if (written > 0) {
                    buf.readerIndex(buf.readerIndex() + written);
                }
                if (buf.hasRemaining()) {
                    if (remaining == null) {
                        remaining = new ArrayList<>();
                    }
                    remaining.add(buf);
                } else {
                    // 已完全写出，释放缓冲区
                    buf.release();
                }
            }

            if (remaining != null) {
                // 部分写出，保留剩余缓冲区等待下次写出
                pendingWriteBufs = remaining;
                submitWrite(remaining);
                return;
            }
        }

        // 全部写出完成，检查 BufferWriter 链表是否还有数据
        if (bufferWriter.getCount() > 0) {
            drainPending();
        }
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

        // 释放部分写出暂存的缓冲区
        if (pendingWriteBufs != null) {
            for (PooledByteBuffer buf : pendingWriteBufs) {
                buf.release();
            }
            pendingWriteBufs = null;
            pendingWriteViews = null;
        }

        try {
            bufferWriter.close();
        } catch (Exception e) {
            logger.error("close bufferWriter failed", e);
        }

        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

        try { channel.shutdownInput(); } catch (IOException e) { logger.error("shutdownInput failed", e); }
        try { channel.shutdownOutput(); } catch (IOException e) { logger.error("shutdownOutput failed", e); }
        try { channel.close(); } catch (IOException e) { logger.error("close channel failed", e); }

        try {
            invokePipeline(ChannelState.CHANNEL_CLOSED, null);
        } catch (Exception e) {
            logger.error("fire CHANNEL_CLOSED failed", e);
        }

        channelPipeline = null;
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
