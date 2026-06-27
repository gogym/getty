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
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.loop.SelectedSelector;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.util.queue.LinkedBlockQueue;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

/**
 * UDP 通道实现。
 * <p>
 * 基于 {@link DatagramChannel} 配合 Selector 实现非阻塞的 UDP 数据报收发。
 * 写操作通过内部队列异步执行，避免阻塞调用方。
 * </p>
 *
 * @author gogym
 */
public class UdpChannel extends AbstractSocketChannel {

    /** UDP 通道 */
    private final DatagramChannel datagramChannel;

    /** 多路复用选择器 */
    private final SelectedSelector selector;

    /** 写出队列 */
    private final LinkedBlockQueue<DatagramPacket> writeQueue;

    /** 工作线程池 */
    private final ThreadPool workerThreadPool;

    /**
     * 构造 UDP 通道。
     *
     * @param datagramChannel    UDP 通道
     * @param selector           选择器
     * @param config             配置
     * @param byteBufferPool     内存池
     * @param channelInitializer 管道初始化器
     * @param workerThreadNum    工作线程数
     */
    public UdpChannel(DatagramChannel datagramChannel, SelectedSelector selector,
                      BaseConfig config, ByteBufferPool byteBufferPool,
                      ChannelInitializer channelInitializer, int workerThreadNum) {
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        this.writeQueue = new LinkedBlockQueue<>(config.getBufferWriterQueueSize());
        this.channelInitializer = channelInitializer;

        try {
            channelInitializer.initChannel(this);
        } catch (Exception e) {
            throw new RuntimeException("channelPipeline init exception", e);
        }

        // 启动异步写出线程
        loopWrite();

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

        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (selector.select(0) > 0) {
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey sk = it.next();
                            it.remove();
                            if (!sk.isReadable()) {
                                continue;
                            }

                            PooledByteBuffer readBuffer = byteBufferPool.acquire(config.getReadBufferSize());
                            try {
                                // 接收 UDP 数据报
                                InetSocketAddress address = (InetSocketAddress) datagramChannel.receive(readBuffer.flipToFill());
                                readBuffer.flipToFlush();

                                if (!readBuffer.hasRemaining()) {
                                    continue;
                                }

                                // 堆内存零拷贝：直接使用底层数组
                                byte[] bytes;
                                ByteBuffer buf = readBuffer.getBuffer();
                                if (buf.hasArray()) {
                                    bytes = buf.array();
                                } else {
                                    bytes = new byte[readBuffer.remaining()];
                                    readBuffer.get(bytes);
                                }

                                // 封装为 DatagramPacket 并输送到管道
                                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address);
                                invokePipeline(ChannelState.CHANNEL_READ, packet);
                            } catch (Exception e) {
                                logger.error("UDP read error", e);
                            } finally {
                                readBuffer.release();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("UDP selector error", e);
                }
            }
        });
    }

    // ==================== 写操作 ====================

    /**
     * 异步写出线程：从队列中取出数据报并发送。
     */
    private void loopWrite() {
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramPacket packet;
                    while ((packet = writeQueue.take()) != null) {
                        send(packet);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("UDP write loop interrupted", e);
                }
            }
        });
    }

    @Override
    public boolean writeAndFlush(Object obj) {
        if (obj instanceof DatagramPacket) {
            try {
                writeQueue.put((DatagramPacket) obj);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("UDP enqueue failed", e);
                return false;
            }
        }
        return true;
    }

    @Override
    public void write(Object obj) {
        // UDP 无连接，write 与 writeAndFlush 语义相同
        writeAndFlush(obj);
    }

    @Override
    public void flush() {
        // UDP 通过 writeQueue 异步发送，无需额外 flush 操作
    }

    @Override
    public void writeToSocket(PooledByteBuffer obj) {
        // UDP 管道传递的是 DatagramPacket，不会到达此处
    }

    /**
     * 发送 UDP 数据报到目标地址。
     *
     * @param packet 待发送的数据报
     */
    private void send(DatagramPacket packet) {
        try {
            PooledByteBuffer byteBuffer = byteBufferPool.acquire(packet.getLength());
            byteBuffer.put(packet.getData());
            datagramChannel.send(byteBuffer.getBuffer(), packet.getSocketAddress());
            byteBuffer.release();
        } catch (IOException e) {
            logger.error("UDP send failed", e);
        }
    }

    // ==================== 关闭 ====================

    @Override
    public void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        // 先标记为已关闭
        status = CHANNEL_STATUS_CLOSED;

        try {
            datagramChannel.close();
        } catch (IOException e) {
            logger.error("close datagramChannel failed", e);
        }

        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

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

    // ==================== 地址 ====================

    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) datagramChannel.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() throws IOException {
        // UDP 是无连接协议，没有固定的远程地址
        return null;
    }

    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || datagramChannel == null) {
            throw new IOException("channel is closed");
        }
    }
}
