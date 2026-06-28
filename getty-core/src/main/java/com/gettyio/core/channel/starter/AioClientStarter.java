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
package com.gettyio.core.channel.starter;

import com.gettyio.core.buffer.pool.GettyByteBufferPool;
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.loop.AioWriteThreadGroup;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLException;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * AIO 客户端启动器。
 * <p>
 * 创建异步 Socket 通道，连接到服务端，并构建 AioChannel 管道。
 * 支持 SSL 握手和连接回调。
 * </p>
 *
 * @author gogym
 */
public class AioClientStarter extends AioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioClientStarter.class);

    /** 客户端配置 */
    private final GettyConfig config;

    /** 已建立的通道 */
    private AbstractSocketChannel aioChannel;

    /**
     * 简单构造。
     *
     * @param host 服务器地址
     * @param port 服务器端口
     */
    public AioClientStarter(String host, int port) {
        this.config = new GettyConfig();
        this.config.setClient(true);
        this.config.setHost(host);
        this.config.setPort(port);
    }

    /**
     * 指定配置构造。
     *
     * @param config 客户端配置
     */
    public AioClientStarter(GettyConfig config) {
        this.config = config;
        this.config.setClient(true);
    }

    /**
     * 设置管道初始化器。
     */
    public AioClientStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    /**
     * 启动客户端（阻塞等待连接结果）。
     *
     * @throws Exception 连接失败时抛出
     */
    public final void start() throws Exception {
        try {
            start0(null);
        } catch (Exception e) {
            LOGGER.error("start failed", e);
            throw e;
        }
    }

    /**
     * 启动客户端（回调模式）。
     *
     * @param connectHandler 连接回调
     */
    public final void start(ConnectHandler connectHandler) {
        try {
            start0(connectHandler);
        } catch (Exception e) {
            LOGGER.error("start failed", e);
            if (connectHandler != null) {
                connectHandler.onFailed(e);
            }
        }
    }

    /**
     * 内部启动逻辑。
     */
    private void start0(ConnectHandler connectHandler) throws Exception {
        startCheck(config);
        byteBufferPool = new GettyByteBufferPool(config.isDirect());
        writeThreadGroup = new AioWriteThreadGroup(1); // 客户端默认 1 个写线程

        asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "getty-aio-client");
                t.setDaemon(false);
                return t;
            }
        });

        startTcp(asynchronousChannelGroup, connectHandler);
    }

    /**
     * 发起非阻塞连接。
     */
    private void startTcp(AsynchronousChannelGroup group, final ConnectHandler connectHandler) throws Exception {
        final AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(group);

        // 设置 Socket 选项
        Map<java.net.SocketOption<Object>, Object> options = config.getSocketOptions();
        if (options != null) {
            for (Map.Entry<java.net.SocketOption<Object>, Object> entry : options.entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        // 异步连接
        socketChannel.connect(
                new InetSocketAddress(config.getHost(), config.getPort()),
                socketChannel,
                new CompletionHandler<Void, AsynchronousSocketChannel>() {

                    @Override
                    public void completed(Void result, AsynchronousSocketChannel ch) {
                        LOGGER.info("connected to server");
                        try {
                            aioChannel = new AioChannel(ch, config,
                                    new ReadCompletionHandler(),
                                    byteBufferPool, channelInitializer,
                                    writeThreadGroup.next(), writeThreadGroup);
                            aioChannel.starRead();

                            if (connectHandler != null) {
                                fireConnectCallback(connectHandler);
                            }
                        } catch (Exception e) {
                            LOGGER.error("create AioChannel failed", e);
                            try { ch.close(); } catch (IOException ex) { /* ignore */ }
                            if (connectHandler != null) {
                                connectHandler.onFailed(e);
                            }
                        }
                    }

                    @Override
                    public void failed(Throwable exc, AsynchronousSocketChannel ch) {
                        LOGGER.error("connect failed", exc);
                        try { ch.close(); } catch (IOException ex) { /* ignore */ }
                        if (connectHandler != null) {
                            connectHandler.onFailed(exc);
                        }
                    }
                });
    }

    /**
     * 触发连接成功回调（处理 SSL 握手场景）。
     */
    private void fireConnectCallback(final ConnectHandler connectHandler) {
        if (aioChannel.getSslHandler() != null) {
            // SSL 场景：等待握手完成后回调
            aioChannel.setSslHandshakeListener(new IHandshakeListener() {
                @Override
                public void onComplete() {
                    LOGGER.info("SSL handshake completed");
                    connectHandler.onCompleted(aioChannel);
                }

                @Override
                public void onFail(SSLException e) {
                    connectHandler.onFailed(e);
                }
            });
        } else {
            // 非 SSL 场景：直接回调
            connectHandler.onCompleted(aioChannel);
        }
    }

    /**
     * 停止客户端。
     */
    public final void shutdown() {
        if (aioChannel != null) {
            aioChannel.close(true);
            aioChannel = null;
        }
        if (asynchronousChannelGroup != null) {
            asynchronousChannelGroup.shutdown();
            asynchronousChannelGroup = null;
        }
        if (writeThreadGroup != null) {
            writeThreadGroup.shutdown();
            writeThreadGroup = null;
        }
        LOGGER.info("getty client shutdown");
    }

    /**
     * 获取已建立的通道。
     *
     * @return AioChannel
     * @throws RuntimeException SSL 握手未完成时抛出
     * @throws NullPointerException 通道未建立时抛出
     */
    public AbstractSocketChannel getChannel() {
        if (aioChannel == null) {
            throw new NullPointerException("channel is null, not connected yet");
        }
        if (aioChannel.getSslHandler() != null && !aioChannel.getSslHandler().isHandshakeCompleted()) {
            aioChannel.close();
            throw new RuntimeException("SSL handshake not yet complete");
        }
        return aioChannel;
    }
}
