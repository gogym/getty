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

import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
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
import java.util.concurrent.TimeUnit;

/**
 * AIO（异步 I/O）通道实现。
 * <p>
 * 基于 {@link AsynchronousSocketChannel}，通过 CompletionHandler 回调机制
 * 实现非阻塞的读写操作。采用无队列直接写出设计：用户线程加锁追加数据到 pendingWrite，
 * 由 AIO 回调线程驱动 drainPending 提交写出，减少系统调用次数。
 * </p>
 *
 * @author gogym
 */
public class AioChannel extends AbstractSocketChannel {

    /** 底层异步 Socket 通道 */
    private final AsynchronousSocketChannel channel;

    /** 读缓冲区（每次读取时从池中获取，读完释放） */
    private RetainableByteBuffer readByteBuffer;

    /**
     * 写出锁：保护 pendingWrite 的并发访问，同时标记是否有写挂起。
     * <p>
     * 锁内状态：
     * <ul>
     *   <li>pendingWrite != null → 有数据等待写出</li>
     *   <li>currentWrite != null → 有数据正在写出（channel.write 挂起中）</li>
     * </ul>
     * 当 pendingWrite == null 且 currentWrite 已清空时，写出流程结束。
     * </p>
     */
    private final Object writeLock = new Object();

    /** 当前正在写出的缓冲区 */
    private RetainableByteBuffer currentWrite;

    /** 待写缓冲区（用户线程加锁追加，永不阻塞） */
    private RetainableByteBuffer pendingWrite;

    /** 读 / 写完成回调 */
    private final ReadCompletionHandler readCompletionHandler;
    private final WriteCompletionHandler writeCompletionHandler;

    /** SSL 处理器 */
    private SSLHandler sslHandler;

    /** SSL 握手监听器 */
    private IHandshakeListener handshakeListener;

    /**
     * 构造 AIO 通道。
     *
     * @param channel                底层异步通道
     * @param config                 通道配置
     * @param readCompletionHandler        读回调处理器
     * @param writeCompletionHandler       写回调处理器
     * @param byteBufferPool         内存池
     * @param channelInitializer     管道初始化器
     */
    public AioChannel(AsynchronousSocketChannel channel, BaseConfig config,
                      ReadCompletionHandler readCompletionHandler,
                      WriteCompletionHandler writeCompletionHandler,
                      ByteBufferPool byteBufferPool, ChannelInitializer channelInitializer) {
        this.channel = channel;
        this.readCompletionHandler = readCompletionHandler;
        this.writeCompletionHandler = writeCompletionHandler;
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.channelInitializer = channelInitializer;

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
        RetainableByteBuffer readBuf = this.readByteBuffer;
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
     * 将数据追加到 pendingWrite，然后尝试启动写出。
     * <p>
     * 无队列设计：用户线程只短暂持有 writeLock 追加数据，永不阻塞。
     * 如果当前有写正在进行（currentWrite != null），数据留在 pendingWrite，
     * 等 writeCompleted 回调时由 drainPending 继续处理。
     * </p>
     */
    @Override
    public void writeToChannel(Object obj) {
        RetainableByteBuffer buf = (RetainableByteBuffer) obj;
        boolean needDrain = false;
        try {
            synchronized (writeLock) {
                if (pendingWrite == null) {
                    pendingWrite = buf;
                } else if (pendingWrite.writableBytes() >= buf.readableBytes()) {
                    // 空间足够，直接合并
                    pendingWrite.writeBytes(buf);
                    buf.release();
                } else {
                    // 空间不足，从池中分配更大的缓冲区
                    int newSize = pendingWrite.readableBytes() + buf.readableBytes();
                    RetainableByteBuffer newBuf = byteBufferPool.acquire(newSize);
                    newBuf.writeBytes(pendingWrite);
                    pendingWrite.release();
                    newBuf.writeBytes(buf);
                    buf.release();
                    pendingWrite = newBuf;
                }
                // 当前没有写挂起时，需要在锁外启动 drainPending
                if (currentWrite == null) {
                    needDrain = true;
                }
            }

            if (needDrain) {
                drainPending();
            }
        } catch (Exception e) {
            logger.error("writeToChannel failed", e);
            // writeBytes 未成功消费时，buf 仍有剩余数据，需释放防止泄漏
            if (buf.hasRemaining()) {
                buf.release();
            }
        }
    }

    /**
     * 设置 position/limit 并发起异步写出。
     * <p>调用前必须已持有 writeLock，且 currentWrite 已设置。</p>
     */
    private void continueWrite(RetainableByteBuffer buf) {
        ByteBuffer bb = buf.getBuffer();
        bb.position(buf.readerIndex());
        bb.limit(buf.writerIndex());
        channel.write(bb, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
    }

    /**
     * 从 pendingWrite 拿数据、提交 channel.write()。
     * <p>
     * 全程持有 writeLock：交换 pendingWrite → currentWrite，提交写出。
     * 处理 currentWrite 的剩余数据（部分写出场景）。
     * </p>
     */
    private void drainPending() {
        boolean writeSubmitted = false;
        synchronized (writeLock) {
            try {
                while (true) {
                    // 处理 currentWrite 的剩余数据（部分写出场景）
                    if (currentWrite != null) {
                        if (currentWrite.hasRemaining()) {
                            continueWrite(currentWrite);
                            writeSubmitted = true;
                            break;
                        }
                        currentWrite.release();
                        currentWrite = null;
                    }

                    // 交换 pendingWrite
                    if (pendingWrite == null) {
                        // 没有更多数据，写出流程结束
                        break;
                    }

                    // 将 pending 直接作为 currentWrite
                    currentWrite = pendingWrite;
                    pendingWrite = null;
                    continueWrite(currentWrite);
                    writeSubmitted = true;
                    break;
                }
            } catch (Exception e) {
                logger.error("drainPending error", e);

                // 将 currentWrite 的数据合并回 pendingWrite，等待下次重试
                if (currentWrite != null) {
                    if (pendingWrite == null) {
                        pendingWrite = currentWrite;
                    } else {
                        pendingWrite.writeBytes(currentWrite);
                        currentWrite.release();
                    }
                    currentWrite = null;
                }

                // 若 channel.write() 已成功提交（writeSubmitted=true），
                // 则 WritePendingException 由 AIO 回调驱动恢复
                if (!writeSubmitted) {
                    // 需要主动恢复：调用 drainPending 重新提交剩余数据
                    // 注意：此处递归调用时当前锁会释放再重入，不会死锁
                    drainPending();
                }
            }
        }
    }

    /**
     * 写出完成回调。处理部分写出，并继续 drainPending。
     */
    public void writeCompleted() {
        boolean needDrain = false;
        synchronized (writeLock) {
            RetainableByteBuffer cw = currentWrite;
            if (cw != null && cw.hasRemaining()) {
                // 部分写出，继续提交剩余数据
                try {
                    continueWrite(cw);
                    return;
                } catch (WritePendingException e) {
                    logger.warn("WritePendingException on writeCompleted continueWrite", e);
                    return;
                }
            }
            // 当前写出完成，释放缓冲区
            if (cw != null) {
                cw.release();
                currentWrite = null;
            }
            // 检查是否还有 pending 数据
            if (pendingWrite != null) {
                needDrain = true;
            }
        }
        if (needDrain) {
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

        RetainableByteBuffer rBuf = readByteBuffer;
        if (rBuf != null) {
            readByteBuffer = null;
            rBuf.release();
        }

        // 释放 currentWrite 和 pendingWrite（统一在 writeLock 内操作）
        synchronized (writeLock) {
            RetainableByteBuffer cw = currentWrite;
            if (cw != null) {
                currentWrite = null;
                cw.release();
            }
            RetainableByteBuffer pw = pendingWrite;
            if (pw != null) {
                pendingWrite = null;
                pw.release();
            }
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
