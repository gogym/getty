/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.channel;

import com.gettyio.core.buffer.ChunkPool;
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
import java.util.concurrent.TimeoutException;

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
    private LinkedBlockQueue<Object> queue;
    private ThreadPool workerThreadPool;

    public UdpChannel(DatagramChannel datagramChannel, Selector selector, BaseConfig config, ChunkPool chunkPool, ChannelPipeline channelPipeline, int workerThreadNum) {
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        this.config = config;
        this.chunkPool = chunkPool;
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
                            it.remove();
                            if (sk.isReadable()) {
                                ByteBuffer readBuffer = chunkPool.allocate(config.getReadBufferSize(), config.getChunkPoolBlockTime());
                                //接收数据
                                InetSocketAddress address = (InetSocketAddress) datagramChannel.receive(readBuffer);
                                if (null != readBuffer) {
                                    readBuffer.flip();
                                    //读取缓冲区数据，输送到责任链
                                    while (readBuffer.hasRemaining()) {
                                        byte[] bytes = new byte[readBuffer.remaining()];
                                        readBuffer.get(bytes, 0, bytes.length);
                                        //读取的数据封装成DatagramPacket
                                        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, address);
                                        //输出到链条
                                        UdpChannel.this.readToPipeline(datagramPacket);
                                    }
                                    chunkPool.deallocate(readBuffer);
                                }
                            }
                        }
                        //it.remove();
                    }
                } catch (IOException e) {
                    logger.error(e);
                } catch (InterruptedException e) {
                    logger.error(e);
                } catch (TimeoutException e) {
                    logger.error(e);
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
                    Object obj;
                    while ((obj = queue.poll()) != null) {
                        UdpChannel.this.send(obj);
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
    public void writeAndFlush(Object obj) {
        try {
            queue.put(obj);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    @Deprecated
    public void writeToChannel(Object obj) {
        try {
            queue.put(obj);
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
     * @params [obj]
     */
    private void send(Object obj) {
        try {
            //转换成udp数据包
            DatagramPacket datagramPacket = (DatagramPacket) obj;
            ByteBuffer byteBuffer = chunkPool.allocate(datagramPacket.getLength(), config.getChunkPoolBlockTime());
            byteBuffer.put(datagramPacket.getData());
            byteBuffer.flip();
            //写出到目标地址
            datagramChannel.send(byteBuffer, datagramPacket.getSocketAddress());
            //释放内存
            chunkPool.deallocate(byteBuffer);
        } catch (ClassCastException e) {
            logger.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } catch (TimeoutException e) {
            logger.error(e);
        }
    }


}
