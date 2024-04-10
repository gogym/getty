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

/**
 * 类名：Starter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/4/8
 */
public abstract class Starter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Starter.class);

    /**
     * Boss线程数，获取cpu核心,核心小于4设置线程为3，大于4设置和cpu核心数一致
     */
    protected int bossThreadNum = Runtime.getRuntime().availableProcessors() < 4 ? 3 : Runtime.getRuntime().availableProcessors();
    /**
     * Boss共享给Worker的线程数，核心小于4设置线程为1，大于4右移两位
     */
    private final int bossShareToWorkerThreadNum = bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2;
    /**
     * Worker线程数
     */
    protected int workerThreadNum = bossThreadNum - bossShareToWorkerThreadNum;

    /**
     * boss线程池
     */
    protected ThreadPool bossThreadPool;

    /**
     * 内存池构造器
     */
    protected ByteBufferPool byteBufferPool;
    /**
     * 责任链对象
     */
    protected ChannelInitializer channelInitializer;

    /**
     * 启动时检查
     *
     * @param config
     */
    protected void startCheck(BaseConfig config) {
        startCheck(config, false);
    }


    protected void startCheck(BaseConfig config, boolean ignoreHost) {

        if (config == null) {
            throw new NullPointerException("config can't null");
        }

        if (!ignoreHost && (null == config.getHost() || "".equals(config.getHost()))) {
            throw new NullPointerException("The host is null.");
        }
        if (0 == config.getPort()) {
            throw new NullPointerException("The port is null.");
        }
        if (channelInitializer == null) {
            throw new RuntimeException("channelInitializer can't be null");
        }
        if (config.isFlowControl()) {
            if (config.getLowWaterMark() >= config.getHighWaterMark()) {
                throw new RuntimeException("lowWaterMark must be small than highWaterMark");
            }
            if (config.getHighWaterMark() >= config.getBufferWriterQueueSize()) {
                LOGGER.warn("HighWaterMark is meaningless if it is greater than BufferWriterQueueSize");
            }
        }
    }


}
