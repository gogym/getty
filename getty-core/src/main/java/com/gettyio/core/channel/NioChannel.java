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
import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import com.gettyio.core.function.Function;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

/**
 * NioChannel.java
 *
 * @description:NIO通道
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class NioChannel extends AbstractSocketChannel implements Function<BufferWriter, Void> {

    /**
     * 通信channel对象
     */
    private final SocketChannel channel;

    /**
     * SSL服务
     */
    private SSLHandler sslHandler;
    private IHandshakeListener handshakeListener;

    /**
     * loop
     */
    private final NioEventLoop nioEventLoop;

    /**
     * 数据输出类
     */
    protected BufferWriter nioBufferWriter;


    /**
     * 输出信号量
     */
    private final Semaphore semaphore = new Semaphore(1);

    public NioChannel(BaseConfig config, java.nio.channels.SocketChannel channel, NioEventLoop nioEventLoop, ByteBufferPool byteBufferPool, ChannelInitializer channelInitializer) {
        this.config = config;
        this.channel = channel;
        this.nioEventLoop = nioEventLoop;
        this.byteBufferPool = byteBufferPool;
        this.nioBufferWriter = new BufferWriter(byteBufferPool, this, config.getBufferWriterQueueSize());
        this.channelInitializer = channelInitializer;
        try {
            //注意该方法可能抛异常
            channelInitializer.initChannel(this);
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
            NioChannel.this.sslHandler.beginHandshake();
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

        if (nioEventLoop != null) {
            try {
                nioEventLoop.shutdown();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
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
        if (channelPipeline != null) {
            channelPipeline = null;
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

    public SocketChannel getSocketChannel() {
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

    /**
     * 设置SSLHandler
     *
     * @return AioChannel
     */
    @Override
    public void setSslHandler(SSLHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    @Override
    public SSLHandler getSslHandler() {
        return this.sslHandler;
    }

    public NioEventLoop getNioEventLoop() {
        return nioEventLoop;
    }

    @Override
    public IHandshakeListener getSslHandshakeListener() {
        return this.handshakeListener;
    }

    @Override
    public void setSslHandshakeListener(IHandshakeListener handshakeListener) {
        this.handshakeListener = handshakeListener;
    }

    @Override
    public Void apply(BufferWriter input) {
        //获取信息量
        if (semaphore.tryAcquire()) {
            RetainableByteBuffer byteBuf;
            while ((byteBuf = input.poll()) != null) {

                if (!byteBuf.hasRemaining()) {
                    //写完及时释放
                    byteBuf.release();
                }
                try {
                    if (NioChannel.this.isInvalid()) {
                        byteBuf.release();
                        throw new IOException("NioChannel is Invalid");
                    }
                    while (byteBuf.hasRemaining()) {
                        ByteBuffer buffer = byteBuf.getBuffer();
                        getSocketChannel().write(buffer);
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
        return null;
    }
}
