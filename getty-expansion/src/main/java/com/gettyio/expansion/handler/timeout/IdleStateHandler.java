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

import com.gettyio.core.constant.IdleState;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.thread.ThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * 空闲状态检测处理器。
 * <p>
 * 通过定时任务检测通道的读写空闲状态，当通道在指定时间窗口内没有发生读或写操作时，
 * 触发 {@link IdleState} 事件到管道中，供下游处理器（如心跳处理器）处理。
 * </p>
 *
 * <p>工作原理：
 * <ul>
 *   <li>每次收到读/写事件时，重置对应的空闲标志</li>
 *   <li>定时任务检查标志：若标志仍为空闲，则触发事件；否则设置标志为空闲</li>
 * </ul>
 * </p>
 *
 * @author gogym
 * @see HeartBeatTimeOutHandler
 */
public class IdleStateHandler extends ChannelAllBoundHandlerAdapter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(IdleStateHandler.class);

    /** 读空闲标志，volatile 保证多线程可见性 */
    private volatile boolean readerIdle = false;

    /** 写空闲标志，volatile 保证多线程可见性 */
    private volatile boolean writerIdle = false;

    /** 定时任务线程池 */
    private final ThreadPool pool;

    /**
     * 以秒为单位创建空闲检测处理器。
     *
     * @param readerIdleTimeSeconds 读空闲超时时间（秒），0 表示不检测
     * @param writerIdleTimeSeconds 写空闲超时时间（秒），0 表示不检测
     */
    public IdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        this(readerIdleTimeSeconds, writerIdleTimeSeconds, TimeUnit.SECONDS);
    }

    /**
     * 以指定时间单位创建空闲检测处理器。
     *
     * @param readerIdleTime 读空闲超时时间，0 表示不检测
     * @param writerIdleTime 写空闲超时时间，0 表示不检测
     * @param unit           时间单位
     */
    public IdleStateHandler(long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        // 单线程即可满足定时调度需求，减少资源消耗
        pool = new ThreadPool(ThreadPool.SingleThread, 1);

        if (readerIdleTime > 0) {
            pool.scheduleWithFixedRate(() -> {
                if (readerIdle) {
                    try {
                        IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.READER_IDLE);
                    } catch (Exception e) {
                        LOGGER.error("trigger reader idle event failed", e);
                    }
                }
                readerIdle = true;
            }, 0, readerIdleTime, unit);
        }

        if (writerIdleTime > 0) {
            pool.scheduleWithFixedRate(() -> {
                if (writerIdle) {
                    try {
                        IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.WRITER_IDLE);
                    } catch (Exception e) {
                        LOGGER.error("trigger writer idle event failed", e);
                    }
                }
                writerIdle = true;
            }, 0, writerIdleTime, unit);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        readerIdle = false;
        super.channelRead(ctx, in);
    }

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        writerIdle = false;
        super.channelWrite(ctx, obj);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        pool.shutdown();
        super.channelClosed(ctx);
    }
}
