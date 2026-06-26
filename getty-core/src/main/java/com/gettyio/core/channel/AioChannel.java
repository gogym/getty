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
     * 标记 AIO 异步写是否进行中。
     * <p>
     * false = 可提交新写操作，true = 上一次 write 尚未完成。
     * </p>
     */
    private boolean writeInFlight;

    /**
     * 当前提交给 AIO 的缓冲区列表，写出完成后统一释放。
     */
    private List<PooledByteBuffer> pendingWriteBufs;

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
        // 通道已关闭，静默释放缓冲区，避免大量 ERROR 日志
        if (status == CHANNEL_STATUS_CLOSED) {
            if (obj instanceof PooledByteBuffer) {
                ((PooledByteBuffer) obj).release();
            }
            return;
        }
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
     */
    private void drainPending() {
        // 如果上一次 write 尚未完成，数据留在 BufferWriter 链表中，等 writeCompleted() 驱动下次写出
        if (writeInFlight) {
            return;
        }

        List<PooledByteBuffer> bufs = new ArrayList<>();
        bufferWriter.pollAll(bufs);
        if (bufs.isEmpty()) {
            return;
        }

        submitWrite(bufs);
    }

    /**
     * 使用 Gathering Write 提交异步写出。
     *
     * @param bufs 待写出的缓冲区列表
     */
    private void submitWrite(List<PooledByteBuffer> bufs) {
        // 为每个缓冲区创建 ByteBuffer 视图（不修改底层 ByteBuffer 的 position/limit）
        ByteBuffer[] views = new ByteBuffer[bufs.size()];
        for (int i = 0; i < bufs.size(); i++) {
            views[i] = bufs.get(i).asByteBuffer();
        }
        pendingWriteBufs = bufs;
        // 标记写操作进行中，drainPending() 看到 true 会跳过，数据留在 BufferWriter 链表
        writeInFlight = true;
        try{
        channel.write(views, 0, views.length, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
        }catch (Exception e){
            logger.error("channel write failed", e);
        }
    }

    /**
     * 写出完成回调。释放已写出的缓冲区。
     */
    public void writeCompleted() {
        writeInFlight = false;

        // 释放本次提交给 AIO 的缓冲区（内核已接收，全部释放）
        List<PooledByteBuffer> bufs = pendingWriteBufs;
        pendingWriteBufs = null;
        if (bufs != null) {
            for (PooledByteBuffer buf : bufs) {
                buf.release();
            }
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

        pendingWriteBufs = null;

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
