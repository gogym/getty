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
package com.gettyio.core.channel.loop;

import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NIO 事件循环。
 * <p>
 * 负责在单线程中轮询 Selector，处理连接建立（OP_CONNECT）、读取（OP_READ）和写入（OP_WRITE）事件。
 * 每个 NioEventLoop 持有一个 Selector，管理多个 NioChannel 的 I/O 事件。
 * </p>
 *
 * @author gogym
 */
public class NioEventLoop implements EventLoop {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioEventLoop.class);

    /** 关闭标志（CAS 保证 wakeup 只调用一次） */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /** 通道配置 */
    private final BaseConfig config;

    /** 包装后的 Selector（含空轮询检测） */
    private final SelectedSelector selector;

    /** 事件循环线程 */
    private final Thread thread;

    /** 内存池 */
    private final ByteBufferPool byteBufferPool;

    /**
     * 构造 NIO 事件循环。
     *
     * @param config         配置
     * @param byteBufferPool 内存池
     * @throws IOException Selector 创建失败时抛出
     */
    public NioEventLoop(BaseConfig config, ByteBufferPool byteBufferPool) throws IOException {
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.thread = new Thread(this::eventLoop, "nio-event-loop");
        this.thread.setDaemon(true);
        try {
            this.selector = new SelectedSelector(Selector.open());
        } catch (IOException e) {
            LOGGER.error("Selector init failed", e);
            throw e;
        }
    }

    @Override
    public void run() {
        thread.start();
    }

    /**
     * 事件循环主体。在 daemon 线程中运行，复用读缓冲区直到关闭。
     */
    private void eventLoop() {
        // 长生命周期读缓冲区：整个事件循环复用，避免每次 read 都 acquire/release
        PooledByteBuffer readBuffer = byteBufferPool.acquire(config.getReadBufferSize());
        try {
            while (!shutdown.get()) {
                try {
                    selector.select();
                } catch (IOException e) {
                    LOGGER.error("select() error", e);
                }

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey sk = it.next();
                    it.remove();

                    Object attachment = sk.attachment();
                    if (!(attachment instanceof NioChannel)) {
                        continue;
                    }

                    NioChannel nioChannel = (NioChannel) attachment;

                    try {
                        if (sk.isConnectable()) {
                            handleConnect(sk, nioChannel);
                        } else {
                            // 独立检查：单次 select 可同时处理读和写
                            if (sk.isReadable()) {
                                handleRead(sk, nioChannel, readBuffer);
                            }
                            if (sk.isWritable()) {
                                nioChannel.doWrite();
                            }
                        }
                    } catch (CancelledKeyException e) {
                        // Key 已取消，忽略
                    } catch (Exception e) {
                        LOGGER.error("event dispatch error for channel", e);
                        nioChannel.close();
                    }
                }
            }
        } finally {
            readBuffer.release();
        }
    }

    /**
     * 处理连接建立事件。
     */
    private void handleConnect(SelectionKey sk, NioChannel nioChannel) {
        java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
        if (!channel.isConnectionPending()) {
            return;
        }
        try {
            channel.finishConnect();
        } catch (IOException e) {
            LOGGER.error("finishConnect failed", e);
            nioChannel.close();
            return;
        }
    }

    /**
     * 处理读事件。从通道读取数据并输送到管道。
     * <p>
     * 复用事件循环级别的读缓冲区，避免每次读操作的 acquire/release 开销。
     * 管道处理是同步的，因此缓冲区可在下一次读循环安全复用。
     * </p>
     */
    private void handleRead(SelectionKey sk, NioChannel nioChannel, PooledByteBuffer readBuffer) {
        java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
        try {
            // 复用缓冲区：重置指针后重新填充
            readBuffer.clear();
            int recCount = channel.read(readBuffer.flipToFill());
            if (recCount == -1) {
                // 对端关闭连接
                nioChannel.close();
                return;
            }
            if (recCount == 0) {
                return;
            }
        } catch (Exception e) {
            LOGGER.error("channel read error", e);
            nioChannel.close();
            return;
        }

        // 切换到读模式
        readBuffer.flipToFlush();

        if (readBuffer.isReadable()) {
            // 零拷贝：直接传递 PooledByteBuffer（管道同步消费，下一次 read 前数据已被处理）
            nioChannel.doRead(readBuffer);
        }
    }

    @Override
    public void shutdown() {
        // CAS 保证 wakeup 只调用一次，避免重复唤醒
        if (shutdown.compareAndSet(false, true)) {
            selector.wakeup();
        }
        if (selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException e) {
                LOGGER.error("close selector failed", e);
            }
        }
    }

    @Override
    public SelectedSelector getSelector() {
        return selector;
    }
}
