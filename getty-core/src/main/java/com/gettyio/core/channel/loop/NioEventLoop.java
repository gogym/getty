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
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * NIO 事件循环。
 * <p>
 * 负责在单线程中轮询 Selector，处理连接建立（OP_CONNECT）和数据读取（OP_READ）事件。
 * 每个 NioEventLoop 持有一个 Selector，管理多个 NioChannel 的 I/O 事件。
 * </p>
 *
 * @author gogym
 */
public class NioEventLoop implements EventLoop {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioEventLoop.class);

    /** 关闭标志 */
    private volatile boolean shutdown;

    /** 通道配置 */
    private final BaseConfig config;

    /** 包装后的 Selector（含空轮询检测） */
    private SelectedSelector selector;

    /** 单线程池，负责运行事件循环 */
    private final ThreadPool workerThreadPool;

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
        this.workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);
        try {
            this.selector = new SelectedSelector(Selector.open());
        } catch (IOException e) {
            LOGGER.error("Selector init failed", e);
            throw e;
        }
    }

    @Override
    public void run() {
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (!shutdown) {
                    try {
                        selector.select(0);
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

                        if (sk.isConnectable()) {
                            handleConnect(sk, nioChannel);
                        } else if (sk.isReadable()) {
                            handleRead(sk, nioChannel);
                        }
                    }
                }
            }
        });
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
     * 堆内存场景使用零拷贝方式（直接访问底层数组），避免每次读取分配新数组。
     * </p>
     */
    private void handleRead(SelectionKey sk, NioChannel nioChannel) {
        java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
        RetainableByteBuffer readBuffer = null;
        try {
            readBuffer = byteBufferPool.acquire(config.getReadBufferSize());
            int recCount = channel.read(readBuffer.flipToFill());
            if (recCount == -1) {
                // 对端关闭连接
                readBuffer.release();
                readBuffer = null;
                nioChannel.close();
                return;
            }
        } catch (Exception e) {
            LOGGER.error("channel read error", e);
            if (readBuffer != null) {
                readBuffer.release();
                readBuffer = null;
            }
            nioChannel.close();
            return;
        }

        // 切换到读模式
        readBuffer.flipToFlush();

        if (readBuffer.hasRemaining()) {
            // 堆内存零拷贝：直接使用底层数组，避免分配新数组
            byte[] bytes;
            ByteBuffer buf = readBuffer.getBuffer();
            if (buf.hasArray()) {
                bytes = buf.array();
            } else {
                bytes = new byte[readBuffer.remaining()];
                readBuffer.getBuffer().get(bytes);
            }
            nioChannel.doRead(bytes);
        }

        readBuffer.release();
    }

    @Override
    public void shutdown() {
        shutdown = true;
        if (!workerThreadPool.isShutDown()) {
            workerThreadPool.shutdown();
        }
        if (selector != null && selector.isOpen()) {
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
