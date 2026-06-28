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
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.loop.SelectedSelector;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * UDP 通道实现。
 * <p>
 * 基于 {@link DatagramChannel} 配合 Selector 实现非阻塞的 UDP 数据报收发。
 * 读操作通过 Selector 驱动，复用单个读缓冲区；
 * 写操作复用 {@link BufferWriter}，通过 {@code ByteBuffer.wrap()} 零拷贝发送，
 * 业务线程不参与 IO。
 * </p>
 *
 * @author gogym
 */
public class UdpChannel extends AbstractSocketChannel implements FlushNotifier {

    /** UDP 通道 */
    private final DatagramChannel datagramChannel;

    /** 多路复用选择器 */
    private final SelectedSelector selector;

    /** 关闭标志（CAS 保证只关闭一次） */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /** 写出缓冲区：业务线程入队，写线程出队发送 */
    private final BufferWriter bufferWriter;

    /** 读线程 */
    private Thread readThread;

    /** 写线程 */
    private Thread writeThread;

    /**
     * 构造 UDP 通道。
     *
     * @param datagramChannel    UDP 通道
     * @param selector           选择器
     * @param config             配置
     * @param byteBufferPool     内存池
     * @param channelInitializer 管道初始化器
     * @param workerThreadNum    工作线程数（保留参数兼容性，未使用）
     */
    public UdpChannel(DatagramChannel datagramChannel, SelectedSelector selector,
                      GettyConfig config, ByteBufferPool byteBufferPool,
                      ChannelInitializer channelInitializer, int workerThreadNum) {
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.channelInitializer = channelInitializer;
        this.bufferWriter = new BufferWriter(this);

        try {
            channelInitializer.initChannel(this);
        } catch (Exception e) {
            throw new RuntimeException("channelPipeline init exception", e);
        }

        // 启动写出线程（daemon：由读线程或 accept 线程保持 JVM 存活）
        writeThread = new Thread(this::writeLoop, "udp-write");
        writeThread.setDaemon(true);
        writeThread.start();

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

        readThread = new Thread(this::readLoop, "udp-read");
        // 非 daemon：作为 UDP 通道生命线，保持 JVM 存活直到 close() 被调用
        readThread.start();
    }

    /**
     * 读循环。在 daemon 线程中运行，复用单个读缓冲区接收 UDP 数据报。
     */
    private void readLoop() {
        PooledByteBuffer readBuffer = byteBufferPool.acquire(config.getReadBufferSize());
        try {
            while (!shutdown.get()) {
                try {
                    if (selector.select(1000) <= 0) {
                        continue;
                    }

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    SelectionKey[] keys = selectedKeys.toArray(new SelectionKey[selectedKeys.size()]);
                    selectedKeys.clear();

                    for (int idx = 0; idx < keys.length; idx++) {
                        SelectionKey sk = keys[idx];
                        if (!sk.isReadable()) {
                            continue;
                        }

                        // 复用缓冲区：重置指针后重新填充
                        readBuffer.clear();
                        InetSocketAddress address = (InetSocketAddress) datagramChannel.receive(readBuffer.flipToFill());
                        if (address == null) {
                            continue;
                        }
                        readBuffer.flipToFlush();

                        if (!readBuffer.hasRemaining()) {
                            continue;
                        }

                        // 提取字节并输送到管道
                        byte[] bytes = new byte[readBuffer.remaining()];
                        readBuffer.get(bytes);
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address);
                        try {
                            invokePipeline(ChannelState.CHANNEL_READ, packet);
                        } catch (Exception e) {
                            logger.error("UDP pipeline read error", e);
                        }
                    }
                } catch (Exception e) {
                    if (!shutdown.get()) {
                        logger.error("UDP read error", e);
                    }
                }
            }
        } finally {
            readBuffer.release();
        }
    }

    // ==================== 写操作 ====================

    /**
     * 写线程：从 BufferWriter 批量取出 DatagramPacket 并零拷贝发送。
     * <p>
     * 无数据时通过 {@code LockSupport.park()} 阻塞等待，由 {@link #notifyFlush()} 唤醒。
     * </p>
     */
    private void writeLoop() {
        List<Object> pendingMsgs = new ArrayList<>();
        try {
            while (!shutdown.get()) {
                while (bufferWriter.getCount() == 0 && !shutdown.get()) {
                    LockSupport.park();
                }
                if (shutdown.get()) {
                    break;
                }

                bufferWriter.pollAll(pendingMsgs);
                for (int i = 0; i < pendingMsgs.size(); i++) {
                    DatagramPacket packet = (DatagramPacket) pendingMsgs.get(i);
                    ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
                    datagramChannel.send(buf, packet.getSocketAddress());
                }
                pendingMsgs.clear();
            }
        } catch (Exception e) {
            if (!shutdown.get()) {
                logger.error("UDP write loop error", e);
            }
        }
    }

    @Override
    public boolean writeAndFlush(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
            flush();
        } catch (Exception e) {
            logger.error("writeAndFlush failed", e);
        }
        return true;
    }

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
     * 管道终点：将 DatagramPacket 入队，由写线程拉取并通过 {@code channel.send()} 发送。
     * <p>
     * 仅接受 {@code DatagramPacket} 类型。管道链中须配置
     * {@link com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketEncoder}
     * 将非 DatagramPacket 消息自动包装后再到达此处。
     * </p>
     *
     * @throws IllegalArgumentException 消息类型不是 DatagramPacket 时抛出
     */
    @Override
    public void writeToSocket(Object msg) {
        if (!(msg instanceof DatagramPacket)) {
            throw new IllegalArgumentException(
                    "UdpChannel only accepts DatagramPacket, got: " + (msg == null ? "null" : msg.getClass().getName()));
        }
        try {
            bufferWriter.write(msg);
        } catch (Exception e) {
            logger.error("writeToSocket failed", e);
        }
    }

    // ==================== 关闭 ====================

    @Override
    public void close() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        status = CHANNEL_STATUS_CLOSED;

        // wakeup 使读线程退出 select 阻塞
        selector.wakeup();

        // 关闭 BufferWriter 并唤醒写线程
        try {
            bufferWriter.close();
        } catch (Exception e) {
            logger.error("close bufferWriter failed", e);
        }
        LockSupport.unpark(writeThread);

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

    // ==================== FlushNotifier 实现（唤醒写线程） ====================

    /**
     * 唤醒写线程。
     * <p>
     * 由 {@link BufferWriter#flush()} 在业务线程中调用，
     * 通过 {@code LockSupport.unpark()} 直接唤醒写线程，无需锁对象。
     * </p>
     */
    @Override
    public void notifyFlush() {
        LockSupport.unpark(writeThread);
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
