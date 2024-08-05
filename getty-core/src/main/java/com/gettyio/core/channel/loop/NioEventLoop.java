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
package com.gettyio.core.channel.loop;


import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * @description:nio循环事件处理
 * @author:gogym
 * @date:2020/6/17
 * @copyright: Copyright by gettyio.com
 */
public class NioEventLoop implements EventLoop {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(NioEventLoop.class);

    /**
     * 是否已经关闭
     */
    private boolean shutdown = false;

    /**
     * 配置
     */
    private final BaseConfig config;

    /**
     * selector包装
     */
    private SelectedSelector selector;

    /**
     * 创建一个1个线程的线程池，负责读
     */
    private final ThreadPool workerThreadPool;

    /**
     * 内存池
     */
    protected ByteBufferPool byteBufferPool;

    /**
     * 构造方法
     *
     * @param config
     * @param byteBufferPool
     */
    public NioEventLoop(BaseConfig config, ByteBufferPool byteBufferPool) {
        this.config = config;
        this.byteBufferPool = byteBufferPool;
        this.workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);

        try {
            selector = new SelectedSelector(Selector.open());
        } catch (IOException e) {
            LOGGER.error("selector init exception", e);
        }
    }

    @Override
    public void run() {
        // 使用工作线程池执行读取操作
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                // 在未关闭前持续进行选择和处理操作
                while (!shutdown) {
                    try {
                        // 执行选择操作，0表示不阻塞
                        selector.select(0);
                    } catch (IOException e) {
                        // 记录选择操作中的异常
                        LOGGER.error(e);
                    }
                    // 遍历已选择的键集合
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        // 获取单个选择键
                        SelectionKey sk = it.next();
                        // 获取选择键的附件对象
                        Object obj = sk.attachment();
                        // 从选择键集合中移除当前选择键
                        it.remove();
                        // 检查附件对象是否为NioChannel类型
                        if (obj instanceof NioChannel) {
                            // 将附件对象转换为NioChannel
                            NioChannel nioChannel = (NioChannel) obj;
                            // 获取选择键关联的Socket通道
                            java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
                            // 检查选择键是否处于连接状态
                            if (sk.isConnectable()) {
                                // 如果连接正在建立中，则尝试完成连接
                                if (channel.isConnectionPending()) {
                                    try {
                                        channel.finishConnect();
                                    } catch (IOException e) {
                                        // 记录连接完成操作中的异常，并关闭通道
                                        LOGGER.error(e);
                                        nioChannel.close();
                                        break;
                                    }
                                }
                            } else if (sk.isReadable()) {
                                // 从缓冲区池中获取一个读缓冲区
                                RetainableByteBuffer readBuffer = null;
                                // 尝试读取数据
                                try {
                                    // 根据配置获取适当大小的缓冲区
                                    readBuffer = byteBufferPool.acquire(config.getReadBufferSize());
                                    // 从通道读取数据到缓冲区
                                    int recCount = channel.read(readBuffer.flipToFill());
                                    // 如果读取到的数据量为-1，表示通道已关闭
                                    if (recCount == -1) {
                                        // 释放缓冲区并关闭通道
                                        readBuffer.release();
                                        nioChannel.close();
                                        break;
                                    }
                                } catch (Exception e) {
                                    // 记录读取操作中的异常
                                    LOGGER.error(e);
                                    // 如果缓冲区已分配，则释放缓冲区
                                    if (null != readBuffer) {
                                        readBuffer.release();
                                    }
                                    // 关闭通道
                                    nioChannel.close();
                                    break;
                                }
                                // 准备将数据从缓冲区传输出去
                                readBuffer.flipToFlush();
                                // 从缓冲区读取数据，并通过责任链进行处理
                                while (readBuffer.hasRemaining()) {
                                    // 创建一个与剩余数据长度相同的字节数组
                                    byte[] bytes = new byte[readBuffer.remaining()];
                                    // 从缓冲区中获取数据
                                    readBuffer.getBuffer().get(bytes);
                                    // 处理读取到的数据
                                    nioChannel.doRead(bytes);
                                }
                                // 读取操作完成，释放缓冲区
                                readBuffer.release();
                            }
                        }
                    }
                }
            }
        });
    }


    @Override
    public void shutdown() {
        shutdown = true;
        if (!workerThreadPool.isShutDown()) {
            workerThreadPool.shutdown();
        }

        if (selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException e) {
                LOGGER.error(e);
            }
        }

    }

    @Override
    public SelectedSelector getSelector() {
        return selector;
    }


}
