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
import com.gettyio.core.constant.Banner;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * AIO 服务端启动器。
 * <p>
 * 创建异步 ServerSocketChannel，监听并接受客户端连接。
 * 每个新连接创建一个 AioChannel 管道。
 * </p>
 *
 * @author gogym
 */
public class AioServerStarter extends AioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioServerStarter.class);

    /** 服务端配置 */
    protected final GettyConfig config;

    /** 读回调处理器（所有连接共享） */
    protected ReadCompletionHandler readCompletionHandler;

    /** 写线程数（默认 4，足以支撑万级连接） */
    private int writeThreadNum = 4;

    /** 服务端 Socket 通道 */
    private AsynchronousServerSocketChannel serverSocketChannel;

    /** 简单构造 */
    public AioServerStarter(int port) {
        this.config = new GettyConfig();
        this.config.setPort(port);
    }

    /** 指定 host 构造 */
    public AioServerStarter(String host, int port) {
        this.config = new GettyConfig();
        this.config.setHost(host);
        this.config.setPort(port);
    }

    /** 指定配置构造 */
    public AioServerStarter(GettyConfig config) {
        this.config = config;
    }

    /** 设置写线程数 */
    public AioServerStarter writeThreadNum(int writeThreadNum) {
        if (writeThreadNum > 0) {
            this.writeThreadNum = writeThreadNum;
        }
        return this;
    }

    /** 设置管道初始化器 */
    public AioServerStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    /** 设置 Boss 线程数（最少 3 个） */
    public AioServerStarter bossThreadNum(int threadNum) {
        if (threadNum >= 3) {
            this.bossThreadNum = threadNum;
        }
        return this;
    }

    /**
     * 启动 AIO 服务端。
     *
     * @throws Exception 启动失败时抛出
     */
    public void start() throws Exception {
        Banner.printBanner();
        startCheck(config, true);

        byteBufferPool = new GettyByteBufferPool(config.isDirect());
        writeThreadGroup = new AioWriteThreadGroup(writeThreadNum);
        bossThreadPool = new ThreadPool(ThreadPool.FixedThread, bossThreadNum);
        startTcp();
    }

    /**
     * 启动 TCP 监听。
     */
    private void startTcp() throws IOException {
        readCompletionHandler = new ReadCompletionHandler();

        try {
            asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(bossThreadNum, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "getty-aio-boss");
                    t.setDaemon(true);
                    return t;
                }
            });

            serverSocketChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);

            // 设置 Socket 选项
            Map<SocketOption<Object>, Object> options = config.getSocketOptions();
            if (options != null) {
                for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
                    serverSocketChannel.setOption(entry.getKey(), entry.getValue());
                }
            }

            // 绑定端口（backlog = 1000）
            if (config.getHost() != null) {
                serverSocketChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()), 1000);
            } else {
                serverSocketChannel.bind(new InetSocketAddress(config.getPort()), 1000);
            }

            // 开始接受连接
            bossThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    serverSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {
                        @Override
                        public void completed(AsynchronousSocketChannel result, Object attachment) {
                            createTcpChannel(result);
                            // 继续接受下一个连接
                            serverSocketChannel.accept(null, this);
                        }

                        @Override
                        public void failed(Throwable exc, Object attachment) {
                            if (serverSocketChannel != null) {
                                LOGGER.error("accept failed", exc);
                                serverSocketChannel.accept(null, this);
                            }
                        }
                    });
                }
            });

        } catch (IOException e) {
            shutdown();
            throw e;
        }

        LOGGER.info("getty server started TCP on port {}, bossThreadNum:{}", config.getPort(), bossThreadNum);
        LOGGER.info("getty server config: {}", config);
    }

    /**
     * 停止服务端。
     */
    public final void shutdown() {
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                LOGGER.error("close serverSocketChannel failed", e);
            }
            serverSocketChannel = null;
        }

        if (bossThreadPool != null && !bossThreadPool.isShutDown()) {
            bossThreadPool.shutdownNow();
        }

        if (asynchronousChannelGroup != null && !asynchronousChannelGroup.isShutdown()) {
            try {
                asynchronousChannelGroup.shutdownNow();
            } catch (IOException e) {
                LOGGER.error("shutdown channelGroup failed", e);
            }
            try {
                asynchronousChannelGroup.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("awaitTermination interrupted", e);
            }
        }

        if (writeThreadGroup != null) {
            writeThreadGroup.shutdown();
        }

        LOGGER.info("getty server shutdown");
    }

    /**
     * 为新连接创建 AioChannel。
     */
    private void createTcpChannel(AsynchronousSocketChannel channel) {
        try {
            AbstractSocketChannel aioChannel = new AioChannel(channel, config,
                    readCompletionHandler,
                    byteBufferPool, channelInitializer,
                    writeThreadGroup.next(), writeThreadGroup);
            aioChannel.starRead();
        } catch (Exception e) {
            LOGGER.error("create AioChannel failed", e);
            closeAioChannel(channel);
        }
    }

    /**
     * 安全关闭异步 Socket 通道。
     */
    private static void closeAioChannel(AsynchronousSocketChannel channel) {
        try { channel.shutdownInput(); } catch (IOException e) { /* ignore */ }
        try { channel.shutdownOutput(); } catch (IOException e) { /* ignore */ }
        try { channel.close(); } catch (IOException e) { LOGGER.error("close channel failed", e); }
    }
}
