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
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.UdpChannel;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.loop.NioEventLoop;
import com.gettyio.core.channel.loop.SelectedSelector;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLException;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;

/**
 * NIO 客户端启动器。
 * <p>
 * 支持 TCP 和 UDP 两种模式。TCP 模式通过 Selector 处理连接事件，
 * UDP 模式通过 DatagramChannel 进行数据报通信。
 * </p>
 *
 * @author gogym
 */
public class NioClientStarter extends NioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioClientStarter.class);

    /** 客户端配置 */
    private final GettyConfig config;

    /** 已建立的通道 */
    private AbstractSocketChannel nioChannel;

    /** 事件循环 */
    private NioEventLoop nioEventLoop;

    /** 连接用选择器 */
    private SelectedSelector connectSelector;

    public NioClientStarter(String host, int port) {
        this.config = new GettyConfig();
        this.config.setClient(true);
        this.config.setHost(host);
        this.config.setPort(port);
    }

    public NioClientStarter(GettyConfig config) {
        if (config.getHost() == null || config.getHost().isEmpty()) {
            throw new NullPointerException("host can't be null");
        }
        if (config.getPort() == 0) {
            throw new NullPointerException("port can't be 0");
        }
        this.config = config;
        this.config.setClient(true);
    }

    public NioClientStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    public NioClientStarter socketMode(SocketMode socketMode) {
        this.socketMode = socketMode;
        return this;
    }

    public final void start() throws Exception {
        try {
            start0(null);
        } catch (Exception e) {
            LOGGER.error("start failed", e);
            throw e;
        }
    }

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

    private void start0(ConnectHandler connectHandler) throws Exception {
        startCheck(config);
        byteBufferPool = new GettyByteBufferPool(config.isDirect());
        nioEventLoop = new NioEventLoop(config, byteBufferPool);
        nioEventLoop.run();

        if (socketMode == SocketMode.TCP) {
            startTcp(connectHandler);
        } else {
            startUdp(connectHandler);
        }
    }

    /**
     * TCP 非阻塞连接。
     */
    private void startTcp(final ConnectHandler connectHandler) throws Exception {
        final SocketChannel socketChannel = SocketChannel.open();

        // 设置 Socket 选项
        Map<SocketOption<Object>, Object> options = config.getSocketOptions();
        if (options != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(config.getHost(), config.getPort()));

        // 创建连接专用选择器
        connectSelector = new SelectedSelector(Selector.open());
        socketChannel.register(connectSelector.getSelector(), SelectionKey.OP_CONNECT);

        // 阻塞等待连接完成
        while (connectSelector.select(0) > 0) {
            Iterator<SelectionKey> it = connectSelector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey sk = it.next();
                it.remove();

                if (!sk.isConnectable()) {
                    continue;
                }

                SocketChannel ch = (SocketChannel) sk.channel();
                if (!ch.isConnectionPending()) {
                    continue;
                }

                ch.finishConnect();

                try {
                    nioChannel = new NioChannel(config, socketChannel, nioEventLoop, byteBufferPool, channelInitializer);

                    // 先注册读事件到 EventLoop 的 Selector（确保 channel.keyFor() 可用）
                    // 必须在 connectHandler 回调之前执行，否则回调中的 writeAndFlush
                    // 调用 notifyFlush() 时 keyFor() 返回 null，导致 OP_WRITE 无法注册，数据滞留队列
                    ((NioChannel) nioChannel).register();

                    if (connectHandler != null) {
                        if (nioChannel.getSslHandler() != null) {
                            nioChannel.setSslHandshakeListener(new IHandshakeListener() {
                                @Override
                                public void onComplete() {
                                    LOGGER.info("SSL handshake completed");
                                    connectHandler.onCompleted(nioChannel);
                                }

                                @Override
                                public void onFail(SSLException e) {
                                    connectHandler.onFailed(e);
                                }
                            });
                        } else {
                            connectHandler.onCompleted(nioChannel);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("create NioChannel failed", e);
                    closeChannel(socketChannel);
                    if (connectHandler != null) {
                        connectHandler.onFailed(e);
                    }
                }
            }
            connectSelector.close();
            break;
        }
    }

    /**
     * 启动 UDP 模式。
     */
    private void startUdp(ConnectHandler connectHandler) throws IOException {
        DatagramChannel datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        connectSelector = new SelectedSelector(Selector.open());
        datagramChannel.register(connectSelector.getSelector(), SelectionKey.OP_READ);

        nioChannel = new UdpChannel(datagramChannel, connectSelector, config, byteBufferPool, channelInitializer, 3);
        nioChannel.starRead();

        if (connectHandler != null) {
            connectHandler.onCompleted(nioChannel);
        }
    }

    /**
     * 停止客户端。
     */
    public final void shutdown() {
        if (nioChannel != null) {
            nioChannel.close();
            nioChannel = null;
        }
        if (connectSelector != null && connectSelector.isOpen()) {
            try {
                connectSelector.close();
            } catch (IOException e) {
                LOGGER.error("close selector failed", e);
            }
        }
        if (nioEventLoop != null) {
            nioEventLoop.shutdown();
        }
    }
}
