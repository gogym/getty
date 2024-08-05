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

import com.gettyio.core.buffer.pool.ArrayRetainableByteBufferPool;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLException;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadFactory;


/**
 * AioClientStarter.java
 *
 * @description:aio客户端
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public class AioClientStarter extends AioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioClientStarter.class);

    /**
     * 客户端服务配置
     */
    private ClientConfig clientConfig = new ClientConfig();
    /**
     * aio通道
     */
    private AbstractSocketChannel aioChannel;

    /**
     * 简单启动
     *
     * @param host 服务器地址
     * @param port 服务器端口号
     */
    public AioClientStarter(String host, int port) {
        clientConfig.setHost(host);
        clientConfig.setPort(port);
    }

    /**
     * 配置文件启动
     *
     * @param clientConfig 配置
     */
    public AioClientStarter(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    /**
     * 设置责任链
     *
     * @param channelInitializer 责任链
     * @return AioClientStarter
     */
    public AioClientStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    /**
     * 启动客户端
     *
     * @throws Exception 异常
     */
    public final void start() throws Exception {
        try {
            start0(null);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new Exception(e);
        }
    }

    /**
     * 启动客户端,回调
     *
     * @param connectHandler
     * @throws Exception
     */
    public final void start(ConnectHandler connectHandler) {
        try {
            start0(connectHandler);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            connectHandler.onFailed(e);
        }
    }


    /**
     * 内部启动
     *
     * @param connectHandler
     */
    private void start0(ConnectHandler connectHandler) throws Exception {
        startCheck(clientConfig);
        //初始化内存池
        byteBufferPool = new ArrayRetainableByteBufferPool(10000);

        this.asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable target) {
                return new Thread(target);
            }
        });
        //调用内部启动
        startTcp(asynchronousChannelGroup, connectHandler);
    }


    /**
     * 该方法为非阻塞连接。连接成功与否，会回调
     *
     * @param asynchronousChannelGroup 线程组
     * @param connectHandler           回调
     */
    private void startTcp(AsynchronousChannelGroup asynchronousChannelGroup, final ConnectHandler connectHandler) throws Exception {

        final AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(asynchronousChannelGroup);
        if (clientConfig.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : clientConfig.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }
        /**
         * 非阻塞连接
         */
        socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                LOGGER.info("connect aio server success");
                //连接成功则构造AIOSession对象
                aioChannel = new AioChannel(socketChannel, clientConfig, new ReadCompletionHandler(), new WriteCompletionHandler(), byteBufferPool, channelInitializer);
                //开始读
                aioChannel.starRead();

                if (connectHandler != null) {
                    if (aioChannel.getSslHandler() != null) {
                        aioChannel.setSslHandshakeListener(new IHandshakeListener() {
                            @Override
                            public void onComplete() {
                                LOGGER.info("ssl Handshake Completed");
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        connectHandler.onCompleted(aioChannel);
                                    }
                                }).start();
                            }

                            @Override
                            public void onFail(SSLException e) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        connectHandler.onFailed(e);
                                    }
                                }).start();
                            }
                        });
                    } else {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                connectHandler.onCompleted(aioChannel);
                            }
                        }).start();
                    }
                }
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                LOGGER.error("connect server error", exc);
                if (null != connectHandler) {
                    connectHandler.onFailed(exc);
                }
            }
        });
    }


    /**
     * 停止客户端
     */
    public final void shutdown() {
        if (aioChannel != null) {
            aioChannel.close(true);
            aioChannel = null;
        }
        //仅Client内部创建的ChannelGroup需要shutdown
        if (asynchronousChannelGroup != null) {
            asynchronousChannelGroup.shutdown();
            asynchronousChannelGroup = null;
        }
        LOGGER.info("getty shutdown at " + new Date());
    }

    /**
     * 获取通道
     *
     * @return
     */
    public AbstractSocketChannel getChannel() {
        if (aioChannel != null) {
            if (aioChannel.getSslHandler() != null) {
                //如果开启了ssl,要先判断是否已经完成握手
                if (aioChannel.getSslHandler().isHandshakeCompleted()) {
                    return aioChannel;
                }
                aioChannel.close();
                throw new RuntimeException("The SSL handshcke is not yet complete");
            }
            return aioChannel;
        }
        throw new NullPointerException("aioChannel is null");
    }


}
