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
 * 写出操作由 EventLoop 驱动：业务线程仅将数据追加到 {@link BufferWriter} 链表，
 * 然后通知 EventLoop 注册 OP_WRITE，由 EventLoop 使用 Gathering Write 批量写出。
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
     * OP_WRITE 是否已注册到 Selector。
     * volatile 保证业务线程与 EventLoop 线程间的可见性。
     */
    private volatile boolean writeRegistered;

    /**
     * 部分写出时暂存的缓冲区列表（仅 EventLoop 线程访问）。
     * 下次 doWrite 时优先写出这些缓冲区中的剩余数据。
     */
    private List<PooledByteBuffer> pendingWriteBufs;

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
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        return true;
    }

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
     * 通过 {@link #writeRegistered} 标志避免重复注册。
     * </p>
     */
    @Override
    public void notifyFlush() {
        if (!writeRegistered) {
            writeRegistered = true;
            try {
                SelectionKey key = channel.keyFor(nioEventLoop.getSelector().getSelector());
                if (key != null && key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                }
                // 唤醒 Selector，使其立即处理新注册的 OP_WRITE
                nioEventLoop.getSelector().wakeup();
            } catch (Exception e) {
                // 通道可能已关闭，忽略
                writeRegistered = false;
            }
        }
    }

    // ==================== Gathering Write（由 EventLoop 调用） ====================

    /**
     * 由 EventLoop 在 OP_WRITE 就绪时调用，使用 Gathering Write 批量写出。
     * <p>
     * 写出策略：
     * <ol>
     *   <li>优先写出上次部分写出残留的缓冲区</li>
     *   <li>再从 BufferWriter 链表批量取出新缓冲区</li>
     *   <li>使用 {@code SocketChannel.write(ByteBuffer[])} 一次性写出</li>
     *   <li>部分写出时保留剩余缓冲区，等待下次 OP_WRITE 继续写出</li>
     *   <li>全部写出完成后移除 OP_WRITE 兴趣</li>
     * </ol>
     * </p>
     */
    public void doWrite() {
        if (isInvalid()) {
            return;
        }

        try {
            // 1. 收集所有待写出的缓冲区
            List<PooledByteBuffer> bufs = new ArrayList<>();

            // 优先使用部分写出残留的缓冲区
            if (pendingWriteBufs != null && !pendingWriteBufs.isEmpty()) {
                bufs.addAll(pendingWriteBufs);
                pendingWriteBufs = null;
            }

            // 从 BufferWriter 链表批量取出新缓冲区
            bufferWriter.pollAll(bufs);

            if (bufs.isEmpty()) {
                // 无数据，移除 OP_WRITE
                writeRegistered = false;
                SelectionKey key = channel.keyFor(nioEventLoop.getSelector().getSelector());
                if (key != null && key.isValid()) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
                return;
            }

            // 2. 准备 ByteBuffer 数组，设置 position/limit 为可读范围
            ByteBuffer[] bbArray = new ByteBuffer[bufs.size()];
            for (int i = 0; i < bufs.size(); i++) {
                ByteBuffer bb = bufs.get(i).getBuffer();
                bb.position(bufs.get(i).readerIndex());
                bb.limit(bufs.get(i).writerIndex());
                bbArray[i] = bb;
            }

            // 3. Gathering Write：一次性写出所有缓冲区
            long totalBefore = 0;
            for (ByteBuffer bb : bbArray) {
                totalBefore += bb.remaining();
            }
            long written = channel.write(bbArray);

            // 4. 计算实际写出量，判断是否全部写出
            long totalAfter = 0;
            for (ByteBuffer bb : bbArray) {
                totalAfter += bb.remaining();
            }

            if (totalAfter == 0) {
                // 全部写出完成，同步 BufferWriter 的 readerIndex，释放缓冲区
                for (PooledByteBuffer buf : bufs) {
                    buf.readerIndex(buf.writerIndex());
                    buf.release();
                }
                // 移除 OP_WRITE
                writeRegistered = false;
                SelectionKey key = channel.keyFor(nioEventLoop.getSelector().getSelector());
                if (key != null && key.isValid()) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                // 部分写出：更新 readerIndex，保留剩余数据供下次写出
                List<PooledByteBuffer> remaining = new ArrayList<>();
                for (PooledByteBuffer buf : bufs) {
                    ByteBuffer bb = buf.getBuffer();
                    // 将 ByteBuffer.position 同步回 readerIndex
                    buf.readerIndex(bb.position());
                    if (buf.hasRemaining()) {
                        remaining.add(buf);
                    } else {
                        buf.release();
                    }
                }
                if (!remaining.isEmpty()) {
                    pendingWriteBufs = remaining;
                }
                // 保留 OP_WRITE，等待下次可写时继续
            }

            if (!keepAlive) {
                close();
            }
        } catch (IOException e) {
            logger.error("doWrite gathering write failed", e);
            // 释放残留缓冲区
            if (pendingWriteBufs != null) {
                for (PooledByteBuffer buf : pendingWriteBufs) {
                    buf.release();
                }
                pendingWriteBufs = null;
            }
            close();
        }
    }
}
