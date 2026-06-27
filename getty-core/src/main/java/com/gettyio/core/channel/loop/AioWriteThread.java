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

import com.gettyio.core.channel.AioChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * AIO 共享写线程。
 * <p>
 * 单个写线程管理多个 {@link AioChannel}，通过遍历 + 非阻塞提交实现高效批量写出。
 * AIO 的 {@code channel.write()} 是异步提交，立即返回，实际写出由 OS 并行完成，
 * 因此单线程可同时驱动多个 channel 的写出操作。
 * </p>
 * <p>
 * 唤醒时机（总是 park，完全依赖 wakeup）：
 * <ul>
 *   <li>业务线程 {@code notifyFlush()} → {@code wakeup()}</li>
 *   <li>AIO 回调 {@code writeCompleted()} → {@code wakeup()}</li>
 * </ul>
 * </p>
 *
 * @author gogym
 * @see AioWriteThreadGroup
 */
public class AioWriteThread {

    /** 本线程管理的所有 Channel */
    private final List<AioChannel> channels = new CopyOnWriteArrayList<>();

    /** 底层线程 */
    private final Thread thread;

    /** 关闭标志 */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 构造并启动写线程。
     *
     * @param name 线程名称
     */
    public AioWriteThread(String name) {
        this.thread = new Thread(this::run, name);
        this.thread.setDaemon(true);
    }

    /**
     * 启动写线程。
     */
    public void start() {
        thread.start();
    }

    /**
     * 注册 Channel 到本写线程。
     *
     * @param channel 待注册的通道
     */
    public void register(AioChannel channel) {
        channels.add(channel);
    }

    /**
     * 从本写线程注销 Channel。
     *
     * @param channel 待注销的通道
     */
    public void unregister(AioChannel channel) {
        channels.remove(channel);
    }

    /**
     * 唤醒写线程，使其遍历所有 Channel 处理待写出数据。
     * <p>
     * 可由任意线程调用（业务线程、AIO 回调线程）。
     * </p>
     */
    public void wakeup() {
        LockSupport.unpark(thread);
    }

    /**
     * 关闭写线程。
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            LockSupport.unpark(thread);
        }
    }

    /**
     * 获取管理的 Channel 数量。
     */
    public int getChannelCount() {
        return channels.size();
    }

    /**
     * 写线程主循环。
     * <p>
     * 每次被唤醒后遍历所有 Channel，对每个 Channel 调用 {@link AioChannel#tryDrainAndSubmit()}
     * 尝试 drain + 提交 AIO 写出。遍历完成后总是 park，等待下一次 wakeup。
     * </p>
     * <p>
     * wakeup 来源：
     * 1. 业务线程 notifyFlush() → 有新数据入队
     * 2. AIO 回调 writeCompleted() → 写完成，需处理后续数据
     * </p>
     */
    private void run() {
        while (!shutdown.get()) {
            for (AioChannel ch : channels) {
                if (!ch.isInvalid()) {
                    ch.tryDrainAndSubmit();
                }
            }
            LockSupport.park(this);
        }
    }
}
