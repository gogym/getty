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
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.UdpChannel;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import com.gettyio.core.channel.loop.SelectedSelector;
import com.gettyio.core.constant.Banner;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.util.list.FastArrayList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Map;

/**
 * NIO 服务端启动器。
 * <p>
 * 支持 TCP 和 UDP 两种模式。TCP 模式通过 ServerSocketChannel 接受连接，
 * 并将每个连接分配到 NioEventLoop（轮询负载均衡）。
 * </p>
 *
 * @author gogym
 */
public class NioServerStarter extends NioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioServerStarter.class);

    /** 服务端配置 */
    protected final GettyConfig config;

    /** 服务线程运行标志 */
    private volatile boolean running = true;

    /** TCP 服务通道 */
    private ServerSocketChannel serverSocketChannel;

    /** UDP 通道 */
    private DatagramChannel datagramChannel;

    /** 接受连接用的选择器 */
    private SelectedSelector acceptSelector;

    /** accept 循环线程 */
    private Thread acceptThread;

    /** EventLoop 列表（轮询分配） */
    private final FastArrayList<NioEventLoop> eventLoops = new FastArrayList<>();

    public NioServerStarter(int port) {
        this.config = new GettyConfig();
        this.config.setPort(port);
    }

    public NioServerStarter(String host, int port) {
        this.config = new GettyConfig();
        this.config.setHost(host);
        this.config.setPort(port);
    }

    public NioServerStarter(GettyConfig config) {
        this.config = config;
    }

    public NioServerStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    public NioServerStarter bossThreadNum(int threadNum) {
        if (threadNum >= 3) {
            this.bossThreadNum = threadNum;
        }
        return this;
    }

    public NioServerStarter socketMode(SocketMode socketMode) {
        this.socketMode = socketMode;
        return this;
    }

    /**
     * 启动 NIO 服务端。
     *
     * @throws Exception 启动失败时抛出
     */
    public void start() throws Exception {
        Banner.printBanner();
        startCheck(config, true);
        byteBufferPool = new GettyByteBufferPool(config.isDirect());

        // 创建 EventLoop 池
        for (int i = 0; i < workerThreadNum; i++) {
            NioEventLoop loop = new NioEventLoop(config, byteBufferPool);
            loop.run();
            eventLoops.add(loop);
        }

        if (socketMode == SocketMode.TCP) {
            startTcp();
        } else {
            startUdp();
        }
    }

    /**
     * 启动 TCP 监听。
     */
    private void startTcp() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        if (config.getHost() != null) {
            serverSocketChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()), 1000);
        } else {
            serverSocketChannel.bind(new InetSocketAddress(config.getPort()), 1000);
        }

        acceptSelector = new SelectedSelector(Selector.open());
        serverSocketChannel.register(acceptSelector.getSelector(), SelectionKey.OP_ACCEPT);

        acceptThread = new Thread(this::acceptLoop, "nio-accept");
        // 非 daemon：作为服务端生命线，保持 JVM 存活直到 shutdown() 被调用
        acceptThread.start();

        LOGGER.info("getty server started TCP on port {}, workerThreadNum:{}",
                config.getPort(), workerThreadNum);
        LOGGER.info("getty server config: {}", config);
    }

    /**
     * accept 循环。接受新连接并分配到 EventLoop。
     */
    private void acceptLoop() {
        while (running) {
            try {
                if (acceptSelector.select(0) <= 0) {
                    continue;
                }

                Iterator<SelectionKey> it = acceptSelector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (key.isAcceptable()) {
                        java.nio.channels.SocketChannel socketChannel =
                                ((ServerSocketChannel) key.channel()).accept();
                        socketChannel.configureBlocking(false);
                        createTcpChannel(socketChannel);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    LOGGER.error("accept error", e);
                }
            }
        }
    }

    /**
     * 启动 UDP 监听。
     */
    private void startUdp() throws IOException {
        datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        datagramChannel.bind(new InetSocketAddress(config.getPort()));

        // 设置 Socket 选项
        Map<SocketOption<Object>, Object> options = config.getSocketOptions();
        if (options != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
                datagramChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        acceptSelector = new SelectedSelector(Selector.open());
        datagramChannel.register(acceptSelector.getSelector(), SelectionKey.OP_READ);

        UdpChannel udpChannel = new UdpChannel(datagramChannel, acceptSelector, config,
                byteBufferPool, channelInitializer, workerThreadNum);
        udpChannel.starRead();

        LOGGER.info("getty server started UDP on port {}, bossThreadNum:{}, workerThreadNum:{}",
                config.getPort(), bossThreadNum, workerThreadNum);
        LOGGER.info("getty server config: {}", config);
    }

    /**
     * 为新连接创建 NioChannel（轮询分配到 EventLoop）。
     */
    private void createTcpChannel(java.nio.channels.SocketChannel channel) {
        try {
            NioEventLoop loop = eventLoops.round();
            NioChannel nioChannel = new NioChannel(config, channel, loop, byteBufferPool, channelInitializer);
            nioChannel.register();
        } catch (Exception e) {
            LOGGER.error("create NioChannel failed", e);
            closeChannel(channel);
        }
    }

    /**
     * 停止服务端。
     */
    public final void shutdown() {
        running = false;

        // 先 wakeup 使 accept 线程退出阻塞
        if (acceptSelector != null) {
            acceptSelector.wakeup();
        }

        if (serverSocketChannel != null) {
            try { serverSocketChannel.close(); } catch (IOException e) { LOGGER.error("close serverSocketChannel failed", e); }
            serverSocketChannel = null;
        }

        if (datagramChannel != null) {
            try { datagramChannel.close(); } catch (IOException e) { LOGGER.error("close datagramChannel failed", e); }
            datagramChannel = null;
        }

        if (acceptSelector != null) {
            try { acceptSelector.close(); } catch (IOException e) { LOGGER.error("close selector failed", e); }
            acceptSelector = null;
        }

        for (NioEventLoop loop : eventLoops) {
            loop.shutdown();
        }

        LOGGER.info("getty server shutdown");
    }
}
