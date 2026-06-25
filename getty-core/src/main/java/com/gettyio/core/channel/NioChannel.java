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
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import java.util.function.Function;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

/**
 * NIO（非阻塞 I/O）通道实现。
 * <p>
 * 基于 {@link SocketChannel} 配合 Selector 实现非阻塞读写。
 * 写出操作通过信号量控制并发，确保同一时刻只有一个 flush 循环在执行。
 * </p>
 *
 * @author gogym
 */
public class NioChannel extends AbstractSocketChannel implements Function<BufferWriter, Void> {

    /** 底层 Socket 通道 */
    private final SocketChannel channel;

    /** 所属的事件循环 */
    private final NioEventLoop nioEventLoop;

    /** 数据输出组件 */
    private final BufferWriter bufferWriter;

    /** 写出信号量，保证同一时刻最多一个 flush 循环 */
    private final Semaphore writeSemaphore = new Semaphore(1);

    /** SSL 处理器 */
    private SSLHandler sslHandler;

    /** SSL 握手监听器 */
    private IHandshakeListener handshakeListener;

    /**
     * 构造 NIO 通道。
     *
     * @param config             通道配置
     * @param channel            底层 Socket 通道
     * @param nioEventLoop       所属事件循环
     * @param byteBufferPool     内存池
     * @param channelInitializer 管道初始化器
     */
    public NioChannel(BaseConfig config, SocketChannel channel, NioEventLoop nioEventLoop,
                      ByteBufferPool byteBufferPool, ChannelInitializer channelInitializer) {
        this.config = config;
        this.channel = channel;
        this.nioEventLoop = nioEventLoop;
        this.byteBufferPool = byteBufferPool;
        this.channelInitializer = channelInitializer;
        this.bufferWriter = new BufferWriter(this, config.getBufferWriterQueueSize());

        try {
            channelInitializer.initChannel(this);
        } catch (Exception e) {
            close();
            throw new RuntimeException("SocketChannel init exception", e);
        }

        // 触发新连接事件
        try {
            invokePipeline(ChannelState.NEW_CHANNEL, null);
        } catch (Exception e) {
            logger.error("fire NEW_CHANNEL failed", e);
        }
    }

    // ==================== 注册与读操作 ====================

    /**
     * NIO 的读取由 EventLoop 驱动，此方法为空实现。
     * 注册读事件到 Selector 后，EventLoop 会自动触发读取。
     */
    @Override
    public void starRead() {
        // NIO 读取由 EventLoop 的 select 循环驱动，无需额外操作
    }

    /**
     * 注册读事件到 Selector。如果开启了 SSL，先发起握手。
     *
     * @throws ClosedChannelException 通道已关闭时抛出
     */
    public void register() throws ClosedChannelException {
        if (sslHandler != null) {
            sslHandler.beginHandshake();
        }
        nioEventLoop.getSelector().register(channel, SelectionKey.OP_READ, this);
    }

    /**
     * 处理从 EventLoop 读取到的数据。
     *
     * @param readBuf 读取到的缓冲区
     */
    public void doRead(RetainableByteBuffer readBuf) {
        initiateClose = false;
        try {
            invokePipeline(ChannelState.CHANNEL_READ, readBuf);
        } catch (Exception e) {
            logger.error("pipeline read handler error", e);
            close();
        }
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
            bufferWriter.writeAndFlush((RetainableByteBuffer) obj);
        } catch (Exception e) {
            logger.error("writeToChannel failed", e);
        }
    }

    // ==================== 关闭 ====================

    @Override
    public void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        // 先标记为已关闭，防止并发重入
        status = CHANNEL_STATUS_CLOSED;

        // 通知关闭监听器
        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

        // 仅客户端连接的 EventLoop 需要关闭（服务端的 EventLoop 是共享的）
        if (nioEventLoop != null && config instanceof ClientConfig) {
            try {
                nioEventLoop.shutdown();
            } catch (Exception e) {
                logger.error("shutdown nioEventLoop failed", e);
            }
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
    public InetSocketAddress getLocalAddress() throws IOException {
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

    public NioEventLoop getNioEventLoop() {
        return nioEventLoop;
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
    public IHandshakeListener getSslHandshakeListener() {
        return handshakeListener;
    }

    @Override
    public void setSslHandshakeListener(IHandshakeListener handshakeListener) {
        this.handshakeListener = handshakeListener;
    }

    // ==================== Function 实现（flush 回调） ====================

    @Override
    public Void apply(BufferWriter input) {
        if (!writeSemaphore.tryAcquire()) {
            return null;
        }

        try {
            RetainableByteBuffer byteBuf;
            while ((byteBuf = input.poll()) != null) {
                if (!byteBuf.hasRemaining()) {
                    // 空缓冲区，直接释放
                    byteBuf.release();
                    continue;
                }

                try {
                    if (isInvalid()) {
                        byteBuf.release();
                        throw new IOException("NioChannel is invalid");
                    }
                    // NIO 同步写：循环直到缓冲区数据全部写出
                    ByteBuffer buffer = byteBuf.getBuffer();
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                } catch (IOException e) {
                    logger.error("write to channel failed", e);
                    close();
                } finally {
                    // 确保缓冲区被释放（无论写出成功与否）
                    byteBuf.release();
                }
            }

            if (!keepAlive) {
                close();
            }
        } finally {
            // 确保信号量被释放
            writeSemaphore.release();
        }
        return null;
    }
}
