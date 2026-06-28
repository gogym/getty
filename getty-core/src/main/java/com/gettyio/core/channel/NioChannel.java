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
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * NIO（非阻塞 I/O）通道实现。
 * <p>
 * 基于 {@link SocketChannel} 配合 Selector 实现非阻塞读写。
 * 写出采用 BufferWriter 队列缓存 + Gathering Write 设计：
 * 业务线程将编码器产出的 {@link PooledByteBuffer} 追加到队列后立即返回，
 * EventLoop 在 OP_WRITE 就绪时拉取缓冲区，直接从池化缓冲区获取底层
 * {@code ByteBuffer} 视图构建数组，使用 {@code channel.write(ByteBuffer[])}
 * 批量写出。写完后释放池化缓冲区（跨线程走 MPSC 队列回收）。
 * </p>
 *
 * @author gogym
 */
public class NioChannel extends AbstractSocketChannel implements FlushNotifier {

    /** 底层 Socket 通道 */
    private final SocketChannel channel;

    /** 所属的事件循环 */
    private final NioEventLoop nioEventLoop;

    /** 数据输出组件 */
    private final BufferWriter bufferWriter;

    /**
     * 部分写出时残留的 PooledByteBuffer 列表（仅 EventLoop 线程访问）。
     * 全部写出后释放并清空，等待下一轮 drain。
     */
    private final List<PooledByteBuffer> pendingBufs = new ArrayList<>();

    /**
     * Gathering Write 的 ByteBuffer 视图数组，预分配复用。
     * 仅 EventLoop 线程访问，无并发。
     * 不够大时双倍扩容，避免每次 doWrite 都 new 数组。
     */
    private ByteBuffer[] writeViews = new ByteBuffer[16];

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
        this.bufferWriter = new BufferWriter(this);

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
    public void doRead(PooledByteBuffer readBuf) {
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
            flush();
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        return true;
    }

    /**
     * 经过责任链编码后追加到 BufferWriter 链表，不触发实际写出。
     */
    @Override
    public void write(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error("write failed", e);
        }
    }

    @Override
    public void flush() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        notifyFlush();
    }

    /**
     * 管道终点：将消息入队，由 EventLoop 在 OP_WRITE 就绪时拉取并写出。
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

    // ==================== 关闭 ====================

    @Override
    public void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        // 先标记为已关闭，防止并发重入
        status = CHANNEL_STATUS_CLOSED;

        // 释放残留的 PooledByteBuffer
        for (PooledByteBuffer buf : pendingBufs) {
            buf.release();
        }
        pendingBufs.clear();

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

    // ==================== FlushNotifier 实现（通知 EventLoop 注册 OP_WRITE） ====================

    /**
     * 通知 EventLoop 注册 OP_WRITE。
     * <p>
     * 由 {@link BufferWriter#flush()} 在业务线程中调用。
     * 仅注册兴趣事件并唤醒 Selector，不执行任何 I/O 操作。
     * 不维护额外标志位：{@code interestOps} 是幂等操作，重复注册 OP_WRITE 无副作用。
     * </p>
     */
    @Override
    public void notifyFlush() {
        try {
            SelectionKey key = channel.keyFor(nioEventLoop.getSelector().getSelector());
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
            nioEventLoop.getSelector().wakeup();
        } catch (Exception e) {
            // 通道可能已关闭，忽略
        }
    }

    // ==================== Gathering Write（由 EventLoop 调用） ====================

    /**
     * 由 EventLoop 在 OP_WRITE 就绪时调用，使用 Gathering Write 批量写出。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>从 BufferWriter 拉取所有 PooledByteBuffer，直接获取底层 ByteBuffer 视图构建数组</li>
     *   <li>使用 {@code SocketChannel.write(ByteBuffer[])} Gathering Write 一次性写出</li>
     *   <li>部分写出时保留剩余缓冲区在 pendingBufs 中，等待下次 OP_WRITE 继续写出</li>
     *   <li>全部写完后释放所有 PooledByteBuffer（跨线程走 MPSC 队列回收到业务线程缓存）</li>
     *   <li>再次检查新数据，有数据则继续写出，无数据才移除 OP_WRITE</li>
     * </ol>
     * </p>
     */
    public void doWrite() {
        if (isInvalid()) {
            return;
        }

        try {
            // 1. 从 BufferWriter 拉取新数据
            if (pendingBufs.isEmpty()) {
                bufferWriter.pollAll(pendingBufs);
            }

            if (pendingBufs.isEmpty()) {
                removeOpWrite();
                return;
            }

            // 2. 从 PooledByteBuffer 获取底层 ByteBuffer 视图
            int size = pendingBufs.size();
            if (writeViews.length < size) {
                writeViews = new ByteBuffer[Integer.highestOneBit(size) << 1];
            }
            for (int i = 0; i < size; i++) {
                PooledByteBuffer pBuf = pendingBufs.get(i);
                ByteBuffer bb = pBuf.getBuffer();
                bb.position(pBuf.readerIndex());
                bb.limit(pBuf.writerIndex());
                writeViews[i] = bb;
            }

            // 3. Gathering Write
            channel.write(writeViews, 0, size);

            // 4. 同步 readerIndex，释放已完全写出的缓冲区
            int remaining = 0;
            for (int i = 0; i < size; i++) {
                PooledByteBuffer pBuf = pendingBufs.get(i);
                pBuf.readerIndex(writeViews[i].position());
                if (pBuf.isReadable()) {
                    remaining = pendingBufs.size() - i;
                    // 将未写完的缓冲区前移
                    for (int j = 0; j < remaining; j++) {
                        pendingBufs.set(j, pendingBufs.get(i + j));
                    }
                    break;
                }
                // 已完全写出，释放（跨线程走 MPSC 队列）
                pBuf.release();
            }

            if (remaining > 0) {
                // 部分写出 → 保留 pendingBufs，保持 OP_WRITE
                while (pendingBufs.size() > remaining) {
                    pendingBufs.remove(pendingBufs.size() - 1);
                }
                if (!keepAlive) {
                    close();
                }
                return;
            }

            pendingBufs.clear();

            // 5. 全部写完，再检查一次新数据（防止竞争窗口）
            bufferWriter.pollAll(pendingBufs);
            if (!pendingBufs.isEmpty()) {
                return; // 有新数据，保持 OP_WRITE
            }

            removeOpWrite();

            if (!keepAlive) {
                close();
            }
        } catch (IOException e) {
            logger.error("doWrite gathering write failed", e);
            // 释放残留缓冲区
            for (PooledByteBuffer buf : pendingBufs) {
                buf.release();
            }
            pendingBufs.clear();
            close();
        }
    }

    /**
     * 移除 OP_WRITE 兴趣事件。
     */
    private void removeOpWrite() {
        try {
            SelectionKey key = channel.keyFor(nioEventLoop.getSelector().getSelector());
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        } catch (Exception e) {
            // 通道可能已关闭，忽略
        }
    }

}
