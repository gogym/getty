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
package com.gettyio.core.channel.starter;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.buffer.Time;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.*;
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
    private SocketChannel aioChannel;


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
        if (null == clientConfig.getHost() || "".equals(clientConfig.getHost())) {
            throw new NullPointerException("The connection host is null.");
        }
        if (0 == clientConfig.getPort()) {
            throw new NullPointerException("The connection port is null.");
        }
        this.clientConfig = clientConfig;
    }


    /**
     * 设置责任链
     *
     * @param channelPipeline 责任链
     * @return AioClientStarter
     */
    public AioClientStarter channelInitializer(ChannelPipeline channelPipeline) {
        this.channelPipeline = channelPipeline;
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
     * 启动客户端,连接成功回调
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
            return;
        }
    }


    /**
     * 内部启动
     *
     * @param connectHandler
     */
    private void start0(ConnectHandler connectHandler) throws Exception {
        if (this.channelPipeline == null) {
            throw new NullPointerException("The ChannelPipeline is null.");
        }
        //初始化worker线程池
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);
        //初始化内存池
        chunkPool = new ChunkPool(clientConfig.getClientChunkSize(), new Time(), clientConfig.isDirect());


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
                aioChannel = new AioChannel(socketChannel, clientConfig, new ReadCompletionHandler(workerThreadPool), new WriteCompletionHandler(), chunkPool, channelPipeline);

                if (null != connectHandler) {
                    if (null != aioChannel.getSslHandler()) {
                        aioChannel.setSslHandshakeCompletedListener(new IHandshakeCompletedListener() {
                            @Override
                            public void onComplete() {
                                LOGGER.info("Ssl Handshake Completed");
                                connectHandler.onCompleted(aioChannel);
                            }
                        });
                    } else {
                        connectHandler.onCompleted(aioChannel);
                    }
                }
                aioChannel.starRead();
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
        showdown0(false);
    }


    private void showdown0(boolean flag) {
        if (aioChannel != null) {
            aioChannel.close(flag);
            aioChannel = null;
        }
        //仅Client内部创建的ChannelGroup需要shutdown
        if (asynchronousChannelGroup != null) {
            asynchronousChannelGroup.shutdown();
            asynchronousChannelGroup = null;
        }
    }


    /**
     * 获取AioChannel
     *
     * @return AioChannel
     * @since 1.3.5
     * @deprecated 该方法已过期，请使用ConnectHandler回调来获取channel
     */
    @Deprecated
    public SocketChannel getAioChannel() {
        if (aioChannel != null) {
            if (aioChannel.getSslHandler() != null) {
                //如果开启了ssl,要先判断是否已经完成握手
                if (aioChannel.getSslHandler().getSslService().getSsl().isHandshakeCompleted()) {
                    return aioChannel;
                }
                aioChannel.close();
                throw new RuntimeException("The SSL handshcke is not yet complete");
            }
            return aioChannel;
        }
        throw new NullPointerException("AioChannel was null");
    }
}
