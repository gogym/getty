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

import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.function.Function;
import com.gettyio.core.handler.ssl.SslHandler;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * NioChannel.java
 *
 * @description:NIO通道
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class NioChannel extends SocketChannel implements Function<BufferWriter, Void> {

    private java.nio.channels.SocketChannel channel;
    private Selector selector;

    protected BufferWriter bufferWriter;

    /**
     * 写缓冲
     */
    protected ByteBuffer writeByteBuffer;


    /**
     * SSL服务
     */
    private SslHandler sslHandler;
    private IHandshakeCompletedListener handshakeCompletedListener;


    /**
     * 输出信号量
     */
    private Semaphore semaphore = new Semaphore(1);

    ThreadPool workerThreadPool;
    private ChannelPipeline channelPipeline;

    public NioChannel(java.nio.channels.SocketChannel channel, BaseConfig config, ChunkPool chunkPool, Integer workerThreadNum, ChannelPipeline channelPipeline) {
        this.channel = channel;
        this.config = config;
        this.chunkPool = chunkPool;
        this.chunkPool = chunkPool;
        this.channelPipeline = channelPipeline;
        try {
            this.selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);
            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            close();
            throw new RuntimeException("SocketChannel init exception", e);
        }

        //初始化数据输出类
        bufferWriter = new BufferWriter(BufferWriter.BLOCK, chunkPool, this, config.getBufferWriterQueueSize(), config.getChunkPoolBlockTime());
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        //触发责任链
        try {
            invokePipeline(ChannelState.NEW_CHANNEL);
        } catch (Exception e) {
            logger.error(e);
        }

        //开启线程写出消息
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                NioChannel.this.continueWrite();
            }
        });

    }


    @Override
    public void starRead() {

        initiateClose = false;

        if (NioChannel.this.sslHandler != null) {
            //若开启了SSL，则需要握手
            NioChannel.this.sslHandler.getSslService().beginHandshake(handshakeCompletedListener);
        }
        //多线程处理，提高效率
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (selector.select() > 0) {
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey sk = it.next();
                            //清理事件，提到这里，提高处理消息效率
                            it.remove();
                            if (sk.isConnectable()) {
                                java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
                                //during connecting, finish the connect
                                if (channel.isConnectionPending()) {
                                    channel.finishConnect();
                                }

                            } else if (sk.isReadable()) {
                                ByteBuffer readBuffer = chunkPool.allocate(config.getReadBufferSize(), config.getChunkPoolBlockTime());
                                //接收数据
                                int reccount = ((java.nio.channels.SocketChannel) sk.channel()).read(readBuffer);
                                if (reccount == -1) {
                                    chunkPool.deallocate(readBuffer);
                                    close();
                                    return;
                                }

                                //读取缓冲区数据到管道
                                if (null != readBuffer) {
                                    readBuffer.flip();
                                    //读取缓冲区数据，输送到责任链
                                    while (readBuffer.hasRemaining()) {
                                        byte[] bytes = new byte[readBuffer.remaining()];
                                        readBuffer.get(bytes, 0, bytes.length);
                                        try {
                                            readToPipeline(bytes);
                                        } catch (Exception e) {
                                            logger.error(e);
                                            close();
                                        }
                                    }
                                }
                                //触发读取完成，清理缓冲区
                                chunkPool.deallocate(readBuffer);
                            }
                        }
                        //it.remove();
                    }
                } catch (Exception e) {
                    logger.error(e);
                    try {
                        invokePipeline(ChannelState.INPUT_SHUTDOWN);
                    } catch (Exception e1) {
                        logger.error(e1);
                    }
                    close();
                    return;
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


        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }


        try {
            channel.shutdownInput();
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        }
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("close channel exception", e);
        }
        //更新状态
        status = CHANNEL_STATUS_CLOSED;
        //触发责任链通知
        try {
            invokePipeline(ChannelState.CHANNEL_CLOSED);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //最后需要清空责任链
        if (defaultChannelPipeline != null) {
            defaultChannelPipeline.clean();
            defaultChannelPipeline = null;
        }

    }


    /**
     * 主动关闭
     *
     * @param initiateClose
     */
    @Override
    public synchronized void close(boolean initiateClose) {
        this.initiateClose = initiateClose;
        close();
    }


    @Override
    public void writeAndFlush(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void writeToChannel(Object obj) {
        try {
            byte[] bytes = (byte[]) obj;
            bufferWriter.write(bytes, 0, bytes.length);
        } catch (IOException e) {
            logger.error(e);
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getLocalAddress();
    }


    /**
     * 获取远程地址
     *
     * @return InetSocketAddress
     * @throws IOException 异常
     */
    @Override
    public final InetSocketAddress getRemoteAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    /**
     * 断言
     *
     * @throws IOException 异常
     */
    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || channel == null) {
            throw new IOException("channel is closed");
        }
    }


    /**
     * 继续写
     */
    private void continueWrite() {

        while (true) {
            if (writeByteBuffer == null) {
                writeByteBuffer = bufferWriter.poll();
            } else if (!writeByteBuffer.hasRemaining()) {
                //写完及时释放
                chunkPool.deallocate(writeByteBuffer);
                writeByteBuffer = bufferWriter.poll();
            }

            if (writeByteBuffer != null) {
                //再次写
                try {
                    channel.write(writeByteBuffer);
                } catch (IOException e) {
                    NioChannel.this.close();
                    logger.error("write error", e);
                    break;
                }
            }
            if (!keepAlive) {
                NioChannel.this.close();
                break;
            }
        }
    }


    @Override
    public Void apply(BufferWriter input) {
        return null;
    }


    @Override
    public ChannelPipeline getChannelPipeline() {
        return channelPipeline;
    }

    /**
     * 设置SSLHandler
     *
     * @return AioChannel
     */
    @Override
    public void setSslHandler(SslHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    @Override
    public SslHandler getSslHandler() {
        return this.sslHandler;
    }

    @Override
    public void setSslHandshakeCompletedListener(IHandshakeCompletedListener handshakeCompletedListener) {
        this.handshakeCompletedListener = handshakeCompletedListener;
    }
}
