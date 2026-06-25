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

import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.util.thread.ThreadPool;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * 所有 Starter（AIO / NIO 客户端和服务端）的公共基类。
 * <p>
 * 提供线程数计算、配置校验、内存池和管道初始化器等公共逻辑。
 * </p>
 *
 * @author gogym
 */
public abstract class Starter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Starter.class);

    /**
     * Boss 线程数。CPU 核心 < 4 时设为 3，否则等于核心数。
     */
    protected int bossThreadNum = Math.max(3, Runtime.getRuntime().availableProcessors());

    /**
     * Worker 线程数。根据 CPU 核心数自动调整。
     */
    protected int workerThreadNum = bossThreadNum - (bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2);

    /** Boss 线程池 */
    protected ThreadPool bossThreadPool;

    /** 内存池 */
    protected ByteBufferPool byteBufferPool;

    /** 管道初始化器 */
    protected ChannelInitializer channelInitializer;

    // ==================== 启动校验 ====================

    /**
     * 启动前参数校验（需要 host 的版本）。
     */
    protected void startCheck(BaseConfig config) {
        startCheck(config, false);
    }

    /**
     * 启动前参数校验。
     *
     * @param config       配置
     * @param ignoreHost   是否忽略 host 校验（服务端可忽略）
     */
    protected void startCheck(BaseConfig config, boolean ignoreHost) {
        if (config == null) {
            throw new NullPointerException("config can't be null");
        }
        if (!ignoreHost && (config.getHost() == null || config.getHost().isEmpty())) {
            throw new NullPointerException("host can't be null");
        }
        if (config.getPort() == 0) {
            throw new NullPointerException("port can't be 0");
        }
        if (channelInitializer == null) {
            throw new RuntimeException("channelInitializer can't be null");
        }
        if (config.isFlowControl()) {
            if (config.getLowWaterMark() >= config.getHighWaterMark()) {
                throw new RuntimeException("lowWaterMark must be smaller than highWaterMark");
            }
            if (config.getHighWaterMark() >= config.getBufferWriterQueueSize()) {
                LOGGER.warn("highWaterMark is meaningless if greater than bufferWriterQueueSize");
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 安全关闭 SocketChannel。
     *
     * @param channel 待关闭的通道
     */
    protected static void closeChannel(SocketChannel channel) {
        try { channel.shutdownInput(); } catch (IOException e) { /* ignore */ }
        try { channel.shutdownOutput(); } catch (IOException e) { /* ignore */ }
        try { channel.close(); } catch (IOException e) { LOGGER.error("close channel failed", e); }
    }
}
