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
import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import java.util.function.Function;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * AIO（异步 I/O）通道实现。
 * <p>
 * 基于 {@link AsynchronousSocketChannel}，通过 CompletionHandler 回调机制
 * 实现非阻塞的读写操作。使用信号量控制写出并发，确保同一时刻只有一个写操作在执行。
 * </p>
 *
 * @author gogym
 */
public class AioChannel extends AbstractSocketChannel implements Function<BufferWriter, Void> {

    /** 底层异步 Socket 通道 */
    private final AsynchronousSocketChannel channel;

    /** 读缓冲区（每次读取时从池中获取，读完释放） */
    private RetainableByteBuffer readByteBuffer;

    /** 当前正在写出的缓冲区 */
    private RetainableByteBuffer writeByteBuffer;

    /** 写出信号量，保证同一时刻最多一个写操作 */
    private final Semaphore writeSemaphore = new Semaphore(1);

    /** 读 / 写完成回调 */
    private final ReadCompletionHandler readCompletionHandler;
    private final WriteCompletionHandler writeCompletionHandler;

    /** SSL 处理器 */
    private SSLHandler sslHandler;

    /** SSL 握手监听器 */
    private IHandshakeListener handshakeListener;

    /** 数据输出组件 */
    private BufferWriter bufferWriter;

    /**
     * 构造 AIO 通道。
     *
     * @param channel                底层异步通道
     * @param config                 通道配置
     * @param readCompletionHandler  读回调处理器
     * @param writeCompletionHandler 写回调处理器
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

        // 初始化数据输出组件
        this.bufferWriter = new BufferWriter(byteBufferPool, this, config.getBufferWriterQueueSize());

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

        // 切换到读模式
        readBuf.flipToFlush();

        if (readBuf.hasRemaining()) {
            byte[] bytes;
            // 堆内存零拷贝：直接使用底层数组，避免每次读取都分配新数组
            ByteBuffer buf = readBuf.getBuffer();
            if (buf.hasArray()) {
                bytes = buf.array();
            } else {
                // 直接内存回退：必须拷贝
                bytes = new byte[readBuf.remaining()];
                readBuf.get(bytes);
            }

            try {
                invokePipeline(ChannelState.CHANNEL_READ, bytes);
            } catch (Exception e) {
                logger.error("pipeline read handler error", e);
                // 释放缓冲区防止内存泄漏
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

        // 释放当前缓冲区并发起下一次读取
        readBuf.release();
        continueRead();
    }

    // ==================== 写操作 ====================

    @Override
    public boolean writeAndFlush(Object obj) {
        try {
            if (config.isFlowControl()) {
                int count = bufferWriter.getCount();
                if (count >= config.getHighWaterMark()) {
                    writeable = false;
                    return false;
                }
                if (count <= config.getLowWaterMark()) {
                    writeable = true;
                }
            }
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        return true;
    }

    @Override
    public void writeToChannel(Object obj) {
        try {
            bufferWriter.writeAndFlush((byte[]) obj);
        } catch (Exception e) {
            logger.error("writeToChannel failed", e);
        }
    }

    /**
     * 发起异步写出操作。
     */
    private void continueWrite(ByteBuffer writeBuffer) {
        channel.write(writeBuffer, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
    }

    /**
     * 写出完成回调。如果缓冲区还有剩余数据则继续写，否则取出下一个缓冲区继续。
     * 当所有数据写完后释放信号量。
     */
    public void writeCompleted() {
        if (writeByteBuffer != null && !writeByteBuffer.hasRemaining()) {
            writeByteBuffer.release();
            writeByteBuffer = bufferWriter.poll();
        }

        if (writeByteBuffer != null && writeByteBuffer.hasRemaining()) {
            // 还有数据，继续异步写出（不释放信号量，确保当前线程写完所有数据）
            continueWrite(writeByteBuffer.getBuffer());
            return;
        }

        // 所有数据写完，释放信号量
        writeSemaphore.release();
        if (!keepAlive) {
            close();
        }
    }

    // ==================== 关闭 ====================

    @Override
    public synchronized void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        // 先标记为已关闭，防止并发重入
        status = CHANNEL_STATUS_CLOSED;

        // 释放读缓冲区
        RetainableByteBuffer rBuf = readByteBuffer;
        if (rBuf != null) {
            readByteBuffer = null;
            rBuf.release();
        }

        // 释放写缓冲区
        RetainableByteBuffer wBuf = writeByteBuffer;
        if (wBuf != null) {
            writeByteBuffer = null;
            wBuf.release();
        }

        // 通知关闭监听器
        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

        // 关闭输出组件
        BufferWriter bw = bufferWriter;
        if (bw != null) {
            try {
                if (!bw.isClosed()) {
                    bw.close();
                }
            } catch (IOException e) {
                logger.error("close bufferWriter failed", e);
            }
            bufferWriter = null;
        }

        // 关闭底层通道
        try { channel.shutdownInput(); } catch (IOException e) { logger.error("shutdownInput failed", e); }
        try { channel.shutdownOutput(); } catch (IOException e) { logger.error("shutdownOutput failed", e); }
        try { channel.close(); } catch (IOException e) { logger.error("close channel failed", e); }

        // 触发关闭事件
        try {
            invokePipeline(ChannelState.CHANNEL_CLOSED, null);
        } catch (Exception e) {
            logger.error("fire CHANNEL_CLOSED failed", e);
        }

        // 清空管道引用
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

    // ==================== Function 实现（flush 回调） ====================

    @Override
    public Void apply(BufferWriter input) {
        // 尝试获取信号量，如果获取失败说明已有写操作在进行中
        if (writeSemaphore.tryAcquire()) {
            writeByteBuffer = input.poll();
            if (writeByteBuffer != null && writeByteBuffer.hasRemaining()) {
                continueWrite(writeByteBuffer.getBuffer());
            } else {
                // 无数据可写，立即释放信号量
                writeSemaphore.release();
            }
        }
        return null;
    }
}
