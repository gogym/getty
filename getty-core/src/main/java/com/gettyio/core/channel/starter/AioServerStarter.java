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
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.constant.Banner;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.util.DateTimeUtil;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


/**
 * AioServerStarter.java
 *
 * @description:aio服务器端
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public class AioServerStarter extends AioStarter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioServerStarter.class);

    /**
     * 服务端配置
     */
    protected ServerConfig config = new ServerConfig();
    /**
     * 读回调
     */
    protected ReadCompletionHandler readCompletionHandler;
    /**
     * 写回调
     */
    protected WriteCompletionHandler writeCompletionHandler;

    /**
     * aio服务端通道
     */
    private AsynchronousServerSocketChannel serverSocketChannel;


    /**
     * 简单启动
     *
     * @param port 服务端口
     */
    public AioServerStarter(int port) {
        config.setPort(port);
    }

    /**
     * 指定host启动
     *
     * @param host 服务地址
     * @param port 服务端口
     */
    public AioServerStarter(String host, int port) {
        config.setHost(host);
        config.setPort(port);
    }

    /**
     * 指定配置启动
     *
     * @param config 配置
     */
    public AioServerStarter(ServerConfig config) {
        this.config = config;
    }

    /**
     * 责任链
     *
     * @param channelInitializer 责任链
     * @return AioServerStarter
     */
    public AioServerStarter channelInitializer(ChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }


    /**
     * 设置Boss线程数
     *
     * @param threadNum 线程数
     * @return AioServerStarter
     */
    public AioServerStarter bossThreadNum(int threadNum) {
        if (threadNum >= 3) {
            this.bossThreadNum = threadNum;
        }
        return this;
    }


    /**
     * 启动AIO服务
     *
     * @throws Exception 异常
     */
    public void start() throws Exception {
        //打印框架信息
        Banner.printBanner();
        //启动检查
        startCheck(config, true);

        //实例化内存池
        this.byteBufferPool = new ArrayRetainableByteBufferPool();

        //初始化boss线程池
        bossThreadPool = new ThreadPool(ThreadPool.FixedThread, bossThreadNum);
        //启动
        startTcp();
    }

    /**
     * 启动非阻塞式的TCP
     *
     * @throws IOException
     */
    private final void startTcp() throws IOException {
        try {

            //实例化读写回调
            readCompletionHandler = new ReadCompletionHandler();
            writeCompletionHandler = new WriteCompletionHandler();

            //IO线程分组
            asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(bossThreadNum, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable target) {
                    return new Thread(target);
                }
            });

            //打开服务通道
            this.serverSocketChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
            //设置socket参数
            if (config.getSocketOptions() != null) {
                for (Map.Entry<SocketOption<Object>, Object> entry : config.getSocketOptions().entrySet()) {
                    this.serverSocketChannel.setOption(entry.getKey(), entry.getValue());
                }
            }

            //绑定端口
            if (config.getHost() != null) {
                //服务端socket处理客户端socket连接是需要一定时间的。ServerSocket有一个队列，存放还没有来得及处理的客户端Socket，这个队列的容量就是backlog的含义。
                // 如果队列已经被客户端socket占满了，如果还有新的连接过来，那么ServerSocket会拒绝新的连接。
                // 也就是说backlog提供了容量限制功能，避免太多的客户端socket占用太多服务器资源
                serverSocketChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()), 1000);
            } else {
                serverSocketChannel.bind(new InetSocketAddress(config.getPort()), 1000);
            }

            //开启线程，开始接收客户端的连接
            bossThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    //监听客户端的连接
                    serverSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {
                        @Override
                        public void completed(AsynchronousSocketChannel result, Object attachment) {
                            AioServerStarter.this.createTcpChannel(result);
                            serverSocketChannel.accept(null, this);
                        }

                        @Override
                        public void failed(Throwable exc, Object attachment) {
                            if (serverSocketChannel != null) {
                                LOGGER.error("accept failed at time:" + DateTimeUtil.getCurrentTime(), exc);
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
        LOGGER.info("getty server started TCP on port {},bossThreadNum:{} ,workerThreadNum:{}", config.getPort(), bossThreadNum, workerThreadNum);
        LOGGER.info("getty server config : {}", config.toString());
    }

    /**
     * 停止服务
     */
    public final void shutdown() {
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                LOGGER.error(" serverSocketChannel.close()", e);
            }
            serverSocketChannel = null;
        }

        if (!bossThreadPool.isShutDown()) {
            bossThreadPool.shutdownNow();
        }

        if (!asynchronousChannelGroup.isShutdown()) {
            try {
                asynchronousChannelGroup.shutdownNow();
            } catch (IOException e) {
                LOGGER.error("asynchronousChannelGroup.shutdownNow()", e);
            }
        }

        try {
            //该方法必须在shutdown或shutdownNow后执行,才会生效。否则会造成死锁
            //大概意思是这样的：该方法调用会被阻塞，并且在以下几种情况任意一种发生时都会导致该方法的执行:
            // 即shutdown方法被调用之后，或者参数中定义的timeout时间到达或者当前线程被打断，这几种情况任意一个发生了都会导致该方法在所有任务完成之后才执行。
            boolean b = asynchronousChannelGroup.awaitTermination(5, TimeUnit.SECONDS);
            if (b) {
                LOGGER.info("asynchronousChannelGroup shutdown success at " + new Date());
            }
        } catch (InterruptedException e) {
            LOGGER.error("asynchronousChannelGroup.awaitTermination()", e);
        }
        LOGGER.info("server shutdown at " + new Date());
    }

    /**
     * 为每个新连接创建AioChannel对象
     *
     * @param channel 通道
     */
    private void createTcpChannel(AsynchronousSocketChannel channel) {
        SocketChannel aioChannel = null;
        try {
            aioChannel = new AioChannel(channel, config, readCompletionHandler, writeCompletionHandler, byteBufferPool, channelInitializer);
            //创建成功立即开始读
            aioChannel.starRead();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (aioChannel != null) {
                closeChannel(channel);
            }
        }
    }

    /**
     * 关闭客户端连接通道
     *
     * @param channel 通道
     */
    private void closeChannel(AsynchronousSocketChannel channel) {
        try {
            channel.shutdownInput();
        } catch (IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
