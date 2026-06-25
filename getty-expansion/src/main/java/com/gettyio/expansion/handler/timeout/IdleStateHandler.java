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
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.util.concurrent.TimeUnit;

/**
 * 空闲状态检测处理器。
 * <p>
 * 通过时间轮定时任务检测通道的读写空闲状态，当通道在指定时间窗口内没有发生读或写操作时，
 * 触发 {@link IdleState} 事件到管道中，供下游处理器（如 {@link HeartBeatTimeOutHandler}）处理。
 * </p>
 *
 * <p>工作原理：
 * <ul>
 *   <li>每次收到读/写事件时，重置对应的空闲标志</li>
 *   <li>定时任务检查标志：若标志仍为空闲，则触发事件；否则设置标志为空闲并重新调度下一次检测</li>
 * </ul>
 * </p>
 *
 * <p>时间轮设计：
 * <ul>
 *   <li>使用 {@link HashedWheelTimer} 替代传统的 {@code ScheduledExecutorService}，
 *       调度与取消操作均为 O(1) 时间复杂度</li>
 *   <li>多个 IdleStateHandler 实例可共享同一个 Timer，单线程即可服务成千上万个连接</li>
 *   <li>若未指定 Timer，则创建默认实例（建议在多通道场景下共享传入）</li>
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

    /** 时间轮，多个 IdleStateHandler 可共享同一实例 */
    private final HashedWheelTimer timer;

    /** 是否由本处理器创建 Timer（关闭时需释放） */
    private final boolean ownsTimer;

    /** 读空闲超时时间（纳秒），0 表示不检测 */
    private final long readerIdleTimeNanos;

    /** 写空闲超时时间（纳秒），0 表示不检测 */
    private final long writerIdleTimeNanos;

    /** 当前读空闲定时任务句柄，用于精确取消 */
    private volatile Timeout readerTimeout;

    /** 当前写空闲定时任务句柄，用于精确取消 */
    private volatile Timeout writerTimeout;

    /**
     * 以秒为单位创建空闲检测处理器，使用默认时间轮。
     *
     * @param readerIdleTimeSeconds 读空闲超时时间（秒），0 表示不检测
     * @param writerIdleTimeSeconds 写空闲超时时间（秒），0 表示不检测
     */
    public IdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        this(readerIdleTimeSeconds, writerIdleTimeSeconds, TimeUnit.SECONDS);
    }

    /**
     * 以指定时间单位创建空闲检测处理器，使用默认时间轮。
     *
     * @param readerIdleTime 读空闲超时时间，0 表示不检测
     * @param writerIdleTime 写空闲超时时间，0 表示不检测
     * @param unit           时间单位
     */
    public IdleStateHandler(long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        this(null, readerIdleTime, writerIdleTime, unit);
    }

    /**
     * 以指定时间单位和共享时间轮创建空闲检测处理器。
     * <p>
     * 推荐在多通道场景下传入共享的 {@link HashedWheelTimer} 实例，
     * 使所有 IdleStateHandler 共用一个 worker 线程，大幅减少资源消耗。
     * </p>
     *
     * @param timer          共享时间轮，{@code null} 时自动创建默认实例
     * @param readerIdleTime 读空闲超时时间，0 表示不检测
     * @param writerIdleTime 写空闲超时时间，0 表示不检测
     * @param unit           时间单位
     */
    public IdleStateHandler(HashedWheelTimer timer, long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        this.timer = (timer != null) ? timer : new HashedWheelTimer(100, TimeUnit.MILLISECONDS);
        this.ownsTimer = (timer == null);
        this.readerIdleTimeNanos = unit.toNanos(readerIdleTime);
        this.writerIdleTimeNanos = unit.toNanos(writerIdleTime);

        if (readerIdleTimeNanos > 0) {
            readerTimeout = this.timer.newTimeout(new ReaderIdleTask(), readerIdleTimeNanos, TimeUnit.NANOSECONDS);
        }

        if (writerIdleTimeNanos > 0) {
            writerTimeout = this.timer.newTimeout(new WriterIdleTask(), writerIdleTimeNanos, TimeUnit.NANOSECONDS);
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
        // 精确取消定时任务，避免资源泄漏
        cancelTimeout(readerTimeout);
        cancelTimeout(writerTimeout);

        // 仅当 Timer 由本处理器创建时才停止，共享 Timer 的生命周期由调用方管理
        if (ownsTimer) {
            timer.stop();
        }

        super.channelClosed(ctx);
    }

    /**
     * 安全取消定时任务句柄。
     *
     * @param timeout 任务句柄，可以为 {@code null}
     */
    private static void cancelTimeout(Timeout timeout) {
        if (timeout != null) {
            timeout.cancel();
        }
    }

    // ===================== 定时任务内部类 =====================

    /**
     * 读空闲检测任务。
     * <p>
     * 到期时检查读空闲标志：若为空闲则触发事件，否则设置标志并重新调度下一次检测。
     * </p>
     */
    private final class ReaderIdleTask implements TimerTask {
        @Override
        public void run(Timeout timeout) {
            if (timeout.isCancelled()) {
                return;
            }
            if (readerIdle) {
                try {
                    IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.READER_IDLE);
                } catch (Exception e) {
                    LOGGER.error("trigger reader idle event failed", e);
                }
            }
            readerIdle = true;
            // 重新调度下一次读空闲检测
            if (readerIdleTimeNanos > 0) {
                readerTimeout = timer.newTimeout(this, readerIdleTimeNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * 写空闲检测任务。
     * <p>
     * 到期时检查写空闲标志：若为空闲则触发事件，否则设置标志并重新调度下一次检测。
     * </p>
     */
    private final class WriterIdleTask implements TimerTask {
        @Override
        public void run(Timeout timeout) {
            if (timeout.isCancelled()) {
                return;
            }
            if (writerIdle) {
                try {
                    IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.WRITER_IDLE);
                } catch (Exception e) {
                    LOGGER.error("trigger writer idle event failed", e);
                }
            }
            writerIdle = true;
            // 重新调度下一次写空闲检测
            if (writerIdleTimeNanos > 0) {
                writerTimeout = timer.newTimeout(this, writerIdleTimeNanos, TimeUnit.NANOSECONDS);
            }
        }
    }
}
