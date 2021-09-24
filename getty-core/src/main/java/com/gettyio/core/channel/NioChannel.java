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
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import com.gettyio.core.function.Function;
import com.gettyio.core.handler.ssl.SslHandler;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
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

    private final java.nio.channels.SocketChannel channel;
    /**
     * SSL服务
     */
    private SslHandler sslHandler;
    private IHandshakeCompletedListener handshakeCompletedListener;

    /**
     * 责任链对象
     */
    private final ChannelPipeline channelPipeline;

    /**
     * loop
     */
    private final NioEventLoop nioEventLoop;

    /**
     * 数据输出类
     */
    protected BufferWriter nioBufferWriter;

    private ThreadPool workerThreadPool;

    /**
     * 输出信号量
     */
    private final Semaphore semaphore = new Semaphore(1);

    public NioChannel(BaseConfig config, java.nio.channels.SocketChannel channel, NioEventLoop nioEventLoop, ByteBufAllocator byteBufAllocator, ThreadPool workerThreadPool, ChannelPipeline channelPipeline) {
        this.config = config;
        this.channel = channel;
        this.channelPipeline = channelPipeline;
        this.nioEventLoop = nioEventLoop;
        this.byteBufAllocator = byteBufAllocator;
        this.nioBufferWriter = new BufferWriter(byteBufAllocator, this, config.getBufferWriterQueueSize());
        this.workerThreadPool = workerThreadPool;

        try {
            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            close();
            throw new RuntimeException("SocketChannel init exception", e);
        }

        //触发责任链
        try {
            invokePipeline(ChannelState.NEW_CHANNEL);
        } catch (Exception e) {
            logger.error(e);
        }

    }

    /**
     * 注册事件
     *
     * @throws ClosedChannelException
     */
    public void register() throws ClosedChannelException {
        if (NioChannel.this.sslHandler != null) {
            //若开启了SSL，则需要握手
            NioChannel.this.sslHandler.getSslService().beginHandshake(handshakeCompletedListener);
        }
        //注册事件
        nioEventLoop.getSelector().register(channel, SelectionKey.OP_READ, this);
    }

    /**
     * 读取
     *
     * @param bytes
     */
    public void doRead(byte[] bytes) {
        initiateClose = false;

        try {
            readToPipeline(bytes);
        } catch (Exception e) {
            logger.error(e);
            close();
        }
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
            logger.error(e.getMessage(), e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
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
            logger.error("close channel exception", e);
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
    public boolean writeAndFlush(Object obj) {
        try {
            if (config.isFlowControl()) {
                if (nioBufferWriter.getCount() >= config.getHighWaterMark()) {
                    super.writeable = false;
                    return false;
                }
                if (nioBufferWriter.getCount() <= config.getLowWaterMark()) {
                    super.writeable = true;
                }
            }
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error(e);
        }
        return true;
    }

    @Override
    public void writeToChannel(Object obj) {
        try {
            byte[] bytes = (byte[]) obj;
            nioBufferWriter.writeAndFlush(bytes);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    @Override
    public java.nio.channels.SocketChannel getSocketChannel() {
        return this.channel;
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


    @Override
    public ChannelPipeline getChannelPipeline() {
        return channelPipeline;
    }

    @Override
    public ThreadPool getWorkerThreadPool() {
        return workerThreadPool;
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

    public NioEventLoop getNioEventLoop() {
        return nioEventLoop;
    }

    @Override
    public IHandshakeCompletedListener getSslHandshakeCompletedListener() {
        return this.handshakeCompletedListener;
    }

    @Override
    public void setSslHandshakeCompletedListener(IHandshakeCompletedListener handshakeCompletedListener) {
        this.handshakeCompletedListener = handshakeCompletedListener;
    }

    @Override
    public Void apply(BufferWriter input) {

        //获取信息量
        if (semaphore.tryAcquire()) {
            workerThreadPool.execute(new Runnable() {
                @Override
                public void run() {

                    ByteBuf byteBuf;
                    while ((byteBuf = input.poll()) != null) {

                        if (!byteBuf.isReadable()) {
                            //写完及时释放
                            byteBuf.release();
                        }
                        try {
                            if (NioChannel.this.isInvalid()) {
                                byteBuf.release();
                                throw new IOException("NioChannel is Invalid");
                            }
                            while (byteBuf.isReadable()) {
                                ByteBuffer buffer = byteBuf.getNioBuffer();
                                NioChannel.this.getSocketChannel().write(buffer);
                                byteBuf.readerIndex(buffer.position());
                            }
                        } catch (IOException e) {
                            NioChannel.this.close();
                        }
                        byteBuf.release();
                    }

                    if (!NioChannel.this.isKeepAlive()) {
                        NioChannel.this.close();
                    }
                    //flush完毕后释放信号量
                    semaphore.release();
                }
            });
        }
        return null;
    }
}
