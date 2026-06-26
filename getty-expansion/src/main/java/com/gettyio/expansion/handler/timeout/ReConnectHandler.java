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
package com.gettyio.expansion.handler.timeout;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.loop.SelectedSelector;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLException;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异常断线重连处理器。
 * <p>
 * 当通道因异常断开（非主动关闭）时，自动按递增延迟进行重连。
 * 支持 AIO 和 NIO 两种通道模式，重连策略采用线性递增（attempts * threshold），
 * 最大重试次数可配置。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 *   // 默认：间隔 1000ms，最多重试 3 次
 *   pipeline.addLast(new ReConnectHandler(connectHandler));
 *   // 自定义间隔和次数
 *   pipeline.addLast(new ReConnectHandler(2000, 5, connectHandler));
 * </pre>
 * </p>
 *
 * @author gogym
 */
public class ReConnectHandler extends ChannelInboundHandlerAdapter implements TimerTask {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ReConnectHandler.class);

    /** 默认重连间隔基数（毫秒） */
    private static final long DEFAULT_THRESHOLD = 1000L;

    /** 默认最大重试次数 */
    private static final int DEFAULT_RETRY = 3;

    /** 重连间隔基数（毫秒），延迟 = attempts * threshold */
    private final long threshold;

    /** 最大重试次数 */
    private final int retry;

    /** 连接成功回调 */
    private final ConnectHandler connectHandler;

    /** 定时器 */
    private final HashedWheelTimer timer = new HashedWheelTimer();

    /** 当前通道引用 */
    private AbstractSocketChannel channel;

    /** 当前重连次数 */
    private int attempts = 0;

    /** 防止并发重连 */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public ReConnectHandler(ConnectHandler connectHandler) {
        this(DEFAULT_THRESHOLD, DEFAULT_RETRY, connectHandler);
    }

    public ReConnectHandler(long threshold, ConnectHandler connectHandler) {
        this(threshold, DEFAULT_RETRY, connectHandler);
    }

    public ReConnectHandler(long threshold, int retry, ConnectHandler connectHandler) {
        this.threshold = threshold;
        this.retry = retry;
        this.connectHandler = connectHandler;
    }

    @Override
    public void channelAdded(ChannelHandlerContext ctx) throws Exception {
        this.channel = ctx.channel();
        // 重置重连计数
        attempts = 0;
        reconnecting.set(false);
        super.channelAdded(ctx);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isInitiateClose() && timer.workerState == HashedWheelTimer.WORKER_STATE_INIT) {
            // 非主动关闭时触发重连
            reConnect(ctx.channel());
        }
        super.channelClosed(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (timer.workerState == HashedWheelTimer.WORKER_STATE_INIT) {
            reConnect(ctx.channel());
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        final BaseConfig clientConfig = channel.getConfig();

        if (channel instanceof AioChannel) {
            reconnectAio(clientConfig);
        } else if (channel instanceof NioChannel) {
            reconnectNio(clientConfig);
        }
    }

    /**
     * AIO 模式重连。
     */
    private void reconnectAio(final BaseConfig clientConfig) throws Exception {
        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(
                AsynchronousChannelGroup.withFixedThreadPool(1, r -> new Thread(r)));

        applySocketOptions(socketChannel, clientConfig);

        socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()),
                socketChannel, new java.nio.channels.CompletionHandler<Void, AsynchronousSocketChannel>() {

                    @Override
                    public void completed(Void result, AsynchronousSocketChannel attachment) {
                        LOGGER.info("reconnect AIO server success");
                        channel = new AioChannel(attachment, clientConfig,
                                new ReadCompletionHandler(),
                                channel.getByteBufferPool(), channel.getChannelInitializer());
                        onConnectSuccess(channel);
                        channel.starRead();
                    }

                    @Override
                    public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                        LOGGER.error("reconnect AIO server failed", exc);
                        onConnectFailed(exc);
                    }
                });
    }

    /**
     * NIO 模式重连。
     */
    private void reconnectNio(final BaseConfig clientConfig) {
        try {
            final java.nio.channels.SocketChannel socketChannel = java.nio.channels.SocketChannel.open();
            applySocketOptions(socketChannel, clientConfig);
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()));

            SelectedSelector selector = new SelectedSelector(Selector.open());
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (selector.select() > 0) {
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey sk = it.next();
                    if (sk.isConnectable()) {
                        java.nio.channels.SocketChannel ch = (java.nio.channels.SocketChannel) sk.channel();
                        if (ch.isConnectionPending()) {
                            ch.finishConnect();
                            channel = new NioChannel(clientConfig, socketChannel,
                                    ((NioChannel) channel).getNioEventLoop(),
                                    channel.getByteBufferPool(), channel.getChannelInitializer());
                            onConnectSuccess(channel);
                            ((NioChannel) channel).register();
                        }
                    }
                    it.remove();
                }
            }
        } catch (Exception e) {
            LOGGER.error("reconnect NIO server failed", e);
            onConnectFailed(e);
        }
    }

    /**
     * 应用 Socket 配置选项。
     */
    @SuppressWarnings("unchecked")
    private void applySocketOptions(java.nio.channels.NetworkChannel socketChannel, BaseConfig config) throws Exception {
        if (config.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : config.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 连接成功后的公共处理：SSL 握手监听 + 回调通知。
     * 提取 AIO/NIO 共用的逻辑，避免代码重复。
     */
    private void onConnectSuccess(final AbstractSocketChannel newChannel) {
        reconnecting.set(false);
        if (connectHandler == null) {
            return;
        }
        if (newChannel.getSslHandler() != null) {
            newChannel.setSslHandshakeListener(new IHandshakeListener() {
                @Override
                public void onComplete() {
                    LOGGER.info("SSL handshake completed on reconnect");
                    connectHandler.onCompleted(newChannel);
                }

                @Override
                public void onFail(SSLException e) {
                    connectHandler.onFailed(e);
                }
            });
        } else {
            connectHandler.onCompleted(newChannel);
        }
    }

    /**
     * 连接失败后的公共处理：重新调度重连 + 回调通知。
     */
    private void onConnectFailed(Throwable cause) {
        reconnecting.set(false);
        reConnect(channel);
        if (connectHandler != null) {
            connectHandler.onFailed(cause);
        }
    }

    /**
     * 调度下一次重连。
     */
    private void reConnect(AbstractSocketChannel abstractSocketChannel) {
        if (abstractSocketChannel.isInvalid() && attempts < retry) {
            LOGGER.debug("scheduling reconnect, attempt {}/{}", attempts + 1, retry);
            long delay = attempts * threshold;
            timer.newTimeout(this, delay, TimeUnit.MILLISECONDS);
            attempts++;
        }
    }
}
