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
     * false = 可提交新写操作，true = 上一次 write 尚未完成。
     * </p>
     */
    private boolean writeInFlight;

    /**
     * 复用的收集列表，用于批量取出 BufferWriter 链表中的缓冲区。
     */
    private final List<PooledByteBuffer> drainBufs = new ArrayList<>();

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
     * 数据已由 {@link #writeToSocket} 追加到 BufferWriter 链表，
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
     * 数据经管道编码后到达 {@link #writeToSocket(PooledByteBuffer)}，
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
     * 将数据追加到 BufferWriter 链表（不触发 flush）。
     * <p>
     * 管道终点调用此方法，数据仅入链表。
     * 由 {@link #flush()} 触发 {@link #notifyFlush()} 实际写出。
     * </p>
     */
    @Override
    public void writeToSocket(PooledByteBuffer obj) {
        try {
            bufferWriter.write(obj);
        } catch (Exception e) {
            logger.error("writeToSocket failed", e);
        }
    }

    /**
     * FlushNotifier 实现：通知 AIO 提交写出。
     * <p>
     * AIO 无 OP_WRITE 概念，直接调用 {@link #drainPending()} 尝试提交异步写出。
     * 若上一次 write 尚未完成，{@code channel.write()} 会抛出
     * 由 {@link #writeCompleted()} 回调驱动下次写出。
     * </p>
     */
    @Override
    public void notifyFlush() {
        // 如果上一次 write 尚未完成，数据留在 BufferWriter 链表中，等 writeCompleted() 驱动下次写出
        if (writeInFlight) {
            return;
        }

        drainBufs.clear();
        bufferWriter.pollAll(drainBufs);

        submitWrite(drainBufs);
    }


    /**
     * 使用 Gathering Write 提交异步写出。
     *
     * @param bufs 待写出的缓冲区列表
     */
    private void submitWrite(List<PooledByteBuffer> bufs) {

        if (bufs.isEmpty()) {
            return;
        }

        // 为每个缓冲区创建 ByteBuffer 视图（不修改底层 ByteBuffer 的 position/limit）
        ByteBuffer[] views = new ByteBuffer[bufs.size()];
        for (int i = 0; i < bufs.size(); i++) {
            views[i] = bufs.get(i).asByteBuffer();
        }
        // 标记写操作进行中，writeCompleted() 回调驱动下次写出
        writeInFlight = true;
        try {
            channel.write(views, 0, views.length, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
        } catch (Exception e) {
            logger.error("channel write failed", e);
            // 写出失败，重置状态。
            writeInFlight = false;
            close();
        }
    }

    /**
     * 写出完成回调。释放已写出的缓冲区。
     */
    public void writeCompleted() {
        writeInFlight = false;

        // 释放本次提交给 AIO 的缓冲区（内核已接收，全部释放）
        for (PooledByteBuffer buf : drainBufs) {
            buf.release();
        }
        drainBufs.clear();

        notifyFlush();
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

        // 释放残留缓冲区
        for (PooledByteBuffer buf : drainBufs) {
            buf.release();
        }
        drainBufs.clear();

        try {
            bufferWriter.close();
        } catch (Exception e) {
            logger.error("close bufferWriter failed", e);
        }

        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

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
