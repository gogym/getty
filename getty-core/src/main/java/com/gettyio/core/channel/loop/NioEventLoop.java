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
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.nio.ByteBuffer;
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
        //循环读
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (!shutdown) {
                    try {
                        selector.select(0);
                    } catch (IOException e) {
                        LOGGER.error(e);
                    }
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        Object obj = sk.attachment();

                        if (obj instanceof NioChannel) {
                            NioChannel nioChannel = (NioChannel) obj;
                            java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
                            if (sk.isConnectable()) {
                                //连接过程中，完成连接
                                if (channel.isConnectionPending()) {
                                    try {
                                        channel.finishConnect();
                                    } catch (IOException e) {
                                        LOGGER.error(e);
                                        nioChannel.close();
                                        break;
                                    }
                                }
                            } else if (sk.isReadable()) {
                                ByteBuf readBuffer = null;
                                //接收数据
                                try {
                                    readBuffer = byteBufAllocator.buffer(config.getReadBufferSize());
                                    ByteBuffer readByteBuf = readBuffer.nioBuffer(readBuffer.writerIndex(), readBuffer.writableBytes());
                                    int recCount = channel.read(readByteBuf);
                                    readBuffer.writerIndex(readBuffer.getNioBuffer().flip().remaining());

                                    if (recCount == -1) {
                                        readBuffer.release();
                                        nioChannel.close();
                                        break;
                                    }
                                } catch (Exception e) {
                                    LOGGER.error(e);
                                    if (null != readBuffer) {
                                        readBuffer.release();
                                    }
                                    nioChannel.close();
                                    break;
                                }

                                //读取缓冲区数据，输送到责任链
                                while (readBuffer.isReadable()) {
                                    byte[] bytes = new byte[readBuffer.readableBytes()];
                                    readBuffer.readBytes(bytes);
                                    nioChannel.doRead(bytes);
                                }
                                //触发读取完成，清理缓冲区
                                readBuffer.release();
                            }
                        }
                    }
                    it.remove();
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
