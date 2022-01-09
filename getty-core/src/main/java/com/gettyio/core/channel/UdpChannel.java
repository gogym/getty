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

import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.util.LinkedBlockQueue;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * UdpChannel.java
 *
 * @description:udp通道
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class UdpChannel extends SocketChannel {


    /**
     * udp通道
     */
    private DatagramChannel datagramChannel;
    private Selector selector;
    /**
     * 阻塞队列
     */
    private LinkedBlockQueue<DatagramPacket> queue;
    private ThreadPool workerThreadPool;

    public UdpChannel(DatagramChannel datagramChannel, Selector selector, BaseConfig config, ByteBufAllocator byteBufAllocator, ChannelPipeline channelPipeline, int workerThreadNum) {
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        this.config = config;
        this.byteBufAllocator = byteBufAllocator;
        this.workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        queue = new LinkedBlockQueue<>(config.getBufferWriterQueueSize());
        try {
            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            throw new RuntimeException("channelPipeline init exception", e);
        }
        //开启写监听线程
        loopWrite();
        //触发责任链回调
        try {
            invokePipeline(ChannelState.NEW_CHANNEL);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    @Override
    public void starRead() {
        this.initiateClose = false;

        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (selector.select() > 0) {
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey sk = it.next();
                            if (sk.isReadable()) {
                                ByteBuf readBuffer = byteBufAllocator.ioBuffer(config.getReadBufferSize());
                                ByteBuffer readByteBuf = readBuffer.nioBuffer(readBuffer.writerIndex(), readBuffer.writableBytes());
                                //接收数据
                                InetSocketAddress address = (InetSocketAddress) datagramChannel.receive(readByteBuf);
                                //读取缓冲区数据，输送到责任链
                                readBuffer.writerIndex(readBuffer.getNioBuffer().flip().remaining());
                                while (readBuffer.isReadable()) {
                                    byte[] bytes = new byte[readBuffer.readableBytes()];
                                    readBuffer.readBytes(bytes, 0, bytes.length);
                                    //读取的数据封装成DatagramPacket
                                    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, address);
                                    //输出到链条
                                    UdpChannel.this.readToPipeline(datagramPacket);
                                }
                                readBuffer.release();
                            }
                        }
                        it.remove();
                    }
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        });
    }


    /**
     * 多线程持续写出
     */
    private void loopWrite() {
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramPacket datagramPacket;
                    while ((datagramPacket = queue.take()) != null) {
                        UdpChannel.this.send(datagramPacket);
                    }
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }


    @Override
    public void close() {

        if (status == CHANNEL_STATUS_CLOSED) {
            logger.warn("Channel:{} is closed:", getChannelId());
            return;
        }
        try {
            datagramChannel.close();
        } catch (IOException e) {
            logger.error(e);
        }
        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }
        //更新状态
        status = CHANNEL_STATUS_CLOSED;

        //最后需要清空责任链
        if (defaultChannelPipeline != null) {
            defaultChannelPipeline.clean();
            defaultChannelPipeline = null;
        }
    }

    @Override
    public synchronized void close(boolean initiateClose) {
        this.initiateClose = initiateClose;
        close();
    }

    @Override
    public boolean writeAndFlush(Object obj) {
        try {
            if(obj instanceof DatagramPacket) {
                queue.put((DatagramPacket)obj);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        return true;
    }

    @Override
    @Deprecated
    public void writeToChannel(Object obj) {
        try {
            if(obj instanceof DatagramPacket) {
                queue.put((DatagramPacket)obj);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) datagramChannel.getLocalAddress();
    }

    /**
     * 断言
     *
     * @throws IOException 异常
     */
    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || datagramChannel == null) {
            throw new IOException("channel is closed");
        }
    }


    /**
     * 往目标地址发送消息
     *
     * @return void
     * @params [datagramPacket]
     */
    private void send(DatagramPacket datagramPacket) {
        try {
            //转换成udp数据包
            ByteBuf byteBuffer = byteBufAllocator.ioBuffer(datagramPacket.getLength());
            byteBuffer.writeBytes(datagramPacket.getData());
            //写出到目标地址
            datagramChannel.send(byteBuffer.nioBuffer(), datagramPacket.getSocketAddress());
            //释放内存
            byteBuffer.release();
        } catch (ClassCastException e) {
            logger.error(e.getMessage(), e);
        } catch (IOException e) {
            logger.error(e);
        }
    }


}
