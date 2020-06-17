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
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.*;
import java.util.Map;
import java.util.concurrent.*;


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
    private AsynchronousServerSocketChannel serverSocketChannel = null;

    /**
     * 服务线程运行标志
     */
    private volatile boolean running = true;


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
        if (config == null) {
            throw new NullPointerException("AioServerConfig can't null");
        }

        if (config.getPort() == 0) {
            throw new NullPointerException("AioServerConfig port can't null");
        }
        this.config = config;
    }

    /**
     * 责任链
     *
     * @param channelPipeline 责任链
     * @return AioServerStarter
     */
    public AioServerStarter channelInitializer(ChannelPipeline channelPipeline) {
        this.channelPipeline = channelPipeline;
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

    public AioServerStarter workerThreadNum(int threadNum) {
        if (threadNum >= 3) {
            this.workerThreadNum = threadNum;
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
        LOGGER.info("\r\n" + ServerConfig.BANNER + "\r\n  getty version:(" + ServerConfig.VERSION + ")");

        if (channelPipeline == null) {
            throw new RuntimeException("ChannelPipeline can't be null");
        }

        if (chunkPool == null) {
            //实例化内存池
            this.chunkPool = new ChunkPool(config.getServerChunkSize(), new Time(), config.isDirect());
        }

        //初始化boss线程池
        bossThreadPool = new ThreadPool(ThreadPool.FixedThread, bossThreadNum);
        //初始化worker线程池
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        //启动
        startTcp();
    }

    /**
     * 启动TCP
     *
     * @throws IOException 异常
     */
    private final void startTcp() throws IOException {
        try {

            //实例化读写回调
            readCompletionHandler = new ReadCompletionHandler(workerThreadPool);
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
                    //循环监听客户端的连接
                    while (running) {
                        //调用该方法返回的Future对象的get()方法
                        Future<AsynchronousSocketChannel> future = serverSocketChannel.accept();
                        try {
                            //get方法会阻塞该线程，直到有客户端连接过来，有点类似MQ，所以这种方式是阻塞式的异步IO
                            final AsynchronousSocketChannel channel = future.get();

                            //通过线程池创建客户端连接通道
                            bossThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    //开始创建客户端会话
                                    AioServerStarter.this.createTcpChannel(channel);
                                }
                            });
                        } catch (Exception e) {
                            LOGGER.error("AsynchronousSocketChannel accept Exception", e);
                        }
                    }
                }
            });

        } catch (IOException e) {
            shutdown();
            throw e;
        }
        LOGGER.info("getty server started TCP on port {},bossThreadNum:{} ,workerThreadNum:{}", config.getPort(), bossThreadNum, workerThreadNum);
        LOGGER.info("getty server config is {}", config.toString());
    }


    /**
     * 为每个新连接创建AioChannel对象
     *
     * @param channel 通道
     */
    private void createTcpChannel(AsynchronousSocketChannel channel) {
        SocketChannel aioChannel = null;
        try {
            aioChannel = new AioChannel(channel, config, readCompletionHandler, writeCompletionHandler, chunkPool, channelPipeline);
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

    /**
     * 停止服务
     */
    public final void shutdown() {
        //接收线程标志置为false
        running = false;
        try {
            if (serverSocketChannel != null) {
                serverSocketChannel.close();
                serverSocketChannel = null;
            }


            if (!bossThreadPool.isTerminated()) {
                bossThreadPool.shutdownNow();
            }
            if (!workerThreadPool.isTerminated()) {
                workerThreadPool.shutdownNow();
            }

            if (!asynchronousChannelGroup.isTerminated()) {
                asynchronousChannelGroup.shutdownNow();
            }
            //该方法必须在shutdown或shutdownNow后执行,才会生效。否则会造成死锁
            //大概意思是这样的：该方法调用会被阻塞，并且在以下几种情况任意一种发生时都会导致该方法的执行:
            // 即shutdown方法被调用之后，或者参数中定义的timeout时间到达或者当前线程被打断，这几种情况任意一个发生了都会导致该方法在所有任务完成之后才执行。
            asynchronousChannelGroup.awaitTermination(5, TimeUnit.SECONDS);

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.error("server shutdown exception", e);
        }
    }


}
