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
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
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
 * 实现非阻塞的读写操作。使用信号量控制写出并发，每次只发送一条消息，确保同一时刻只有一个写操作在执行。
 * </p>
 *
 * @author gogym
 */
public class AioChannel extends AbstractSocketChannel implements Function<BufferWriter, Void> {

    /** 底层异步 Socket 通道 */
    private final AsynchronousSocketChannel channel;

    /** 读缓冲区（每次读取时从池中获取，读完释放） */
    private RetainableByteBuffer readByteBuffer;

    /** 当前正在写出的缓冲区（单条消息） */
    private RetainableByteBuffer currentWriteBuf;

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

        // 初始化数据输出组件
        this.bufferWriter = new BufferWriter(this, config.getBufferWriterQueueSize());

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
            RetainableByteBuffer buf = (RetainableByteBuffer) obj;
            // 不在此处设置 position/limit，由 startWrite 在真正写出时设置
            bufferWriter.writeAndFlush(buf);
        } catch (Exception e) {
            logger.error("writeToChannel failed", e);
        }
    }

    /**
     * 发起异步单条消息写出。
     *
     * @param buffer 待写出的 ByteBuffer
     */
    private void continueWrite(ByteBuffer buffer) {
        channel.write(buffer, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
    }

    /**
     * 从队列取出一条消息并写出。
     * 队列为空时释放信号量，并做 double-check 防止遗漏。
     */
    private void drainAndWrite() {
        // 释放已完成的旧缓冲区
        if (currentWriteBuf != null) {
            currentWriteBuf.release();
            currentWriteBuf = null;
        }

        // 从队列中取出一条待写缓冲区
        RetainableByteBuffer buf = bufferWriter.poll();

        if (buf == null) {
            writeSemaphore.release();
            // double-check：释放信号量前可能有新数据入队
            if (bufferWriter.getCount() > 0 && writeSemaphore.tryAcquire()) {
                buf = bufferWriter.poll();
                if (buf != null) {
                    startWrite(buf);
                    return;
                }
                writeSemaphore.release();
            }
            if (!keepAlive && bufferWriter.getCount() == 0) {
                close();
            }
            return;
        }

        startWrite(buf);
    }

    /**
     * 设置当前写出缓冲区并发起写出。
     *
     * @param buf 待写出的 RetainableByteBuffer
     */
    private void startWrite(RetainableByteBuffer buf) {
        currentWriteBuf = buf;
        // 在真正写出前设置 position/limit，确保值准确
        buf.getBuffer().position(buf.readerIndex());
        buf.getBuffer().limit(buf.writerIndex());
        continueWrite(buf.getBuffer());
    }

    /**
     * 写出完成回调。释放已写完的缓冲区，从队列取出下一条消息继续写出。
     * 当所有数据写完后释放信号量。
     */
    public void writeCompleted() {
        // 检查当前消息是否全部写完
        if (currentWriteBuf != null && currentWriteBuf.hasRemaining()) {
            // 部分未写完（partial write），继续写出剩余数据
            continueWrite(currentWriteBuf.getBuffer());
        } else {
            // 当前消息写完，进入下一条
            drainAndWrite();
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

        RetainableByteBuffer curBuf = currentWriteBuf;
        if (curBuf != null) {
            currentWriteBuf = null;
            curBuf.release();
        }

        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

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

    // ==================== Function 实现（flush 回调） ====================

    @Override
    public Void apply(BufferWriter input) {
        if (writeSemaphore.tryAcquire()) {
            RetainableByteBuffer buf = bufferWriter.poll();
            if (buf != null) {
                startWrite(buf);
            } else {
                writeSemaphore.release();
            }
        }
        return null;
    }
}
