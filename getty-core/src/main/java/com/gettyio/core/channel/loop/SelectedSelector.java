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

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 对 JDK Selector 的增强包装。
 * <p>
 * 核心功能：检测并修复 JDK NIO 在 Linux epoll 下的空轮询 bug。
 * 当空轮询次数超过阈值时，自动重建 Selector 并将所有通道迁移过去。
 * </p>
 *
 * @author gogym
 */
public class SelectedSelector extends Selector {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(SelectedSelector.class);

    /**
     * 空轮询重建阈值。如果在一个时间周期内空轮询次数超过此值，
     * 则判定发生了 epoll 空轮询 bug，需要重建 Selector。
     */
    private static final int REBUILD_THRESHOLD = 512;

    /** 默认 select 超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MILLIS = 1000L;

    /** 注册操作进行中标志，用于与 select 线程同步 */
    private volatile boolean registering;

    /** 连续空轮询计数器 */
    private int emptySelectCount;

    /** 底层 Selector */
    private Selector delegate;

    /**
     * 构造方法。
     *
     * @param delegate 底层 Selector
     */
    public SelectedSelector(Selector delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取底层 Selector。
     *
     * @return 底层 Selector 实例
     */
    public Selector getSelector() {
        return delegate;
    }

    // ==================== 注册（与 select 线程同步） ====================

    /**
     * 注册通道到 Selector（无附件）。
     * <p>
     * 通过 wakeup + synchronized 确保注册操作不会与 select() 产生竞争。
     * </p>
     *
     * @param channel 可选择的通道
     * @param op      感兴趣的操作集合
     * @return 注册后的 SelectionKey
     * @throws ClosedChannelException 通道已关闭时抛出
     */
    public SelectionKey register(SelectableChannel channel, int op) throws ClosedChannelException {
        return register(channel, op, null);
    }

    /**
     * 注册通道到 Selector（带附件）。
     *
     * @param channel 可选择的通道
     * @param op      感兴趣的操作集合
     * @param att     附加到 SelectionKey 的对象
     * @return 注册后的 SelectionKey
     * @throws ClosedChannelException 通道已关闭时抛出
     */
    public synchronized SelectionKey register(SelectableChannel channel, int op, Object att)
            throws ClosedChannelException {
        // 标记正在注册，让 select 线程跳过本轮 select
        registering = true;
        // 唤醒可能正在阻塞的 select()
        delegate.wakeup();
        try {
            return channel.register(delegate, op, att);
        } finally {
            registering = false;
        }
    }

    // ==================== Selector 接口实现 ====================

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public SelectorProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<SelectionKey> keys() {
        return delegate.keys();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return delegate.selectedKeys();
    }

    @Override
    public int selectNow() throws IOException {
        return delegate.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        return selectWithRebuild(timeout);
    }

    @Override
    public int select() throws IOException {
        return selectWithRebuild(DEFAULT_TIMEOUT_MILLIS);
    }

    @Override
    public Selector wakeup() {
        return delegate.wakeup();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    // ==================== 空轮询检测 ====================

    /**
     * 带空轮询检测的 select 实现。
     * <p>
     * 如果 select 返回 0 且未超时，则增加空轮询计数。
     * 当计数超过阈值时重建 Selector。
     * </p>
     *
     * @param timeout 超时时间（毫秒）
     * @return 就绪的通道数量
     * @throws IOException I/O 错误
     */
    private int selectWithRebuild(long timeout) throws IOException {
        long startTimeNanos = System.nanoTime();
        emptySelectCount = 0;

        for (; ; ) {
            // 如果有注册操作正在进行，跳过本轮 select
            if (registering) {
                continue;
            }

            int selected = delegate.select(timeout);
            if (selected >= 1) {
                emptySelectCount = 0;
                return selected;
            }

            // 空轮询计数 +1
            emptySelectCount++;
            long elapsed = System.nanoTime() - startTimeNanos;

            if (elapsed >= TimeUnit.MILLISECONDS.toNanos(timeout)) {
                // 正常超时，重置计数器
                emptySelectCount = 0;
            } else if (emptySelectCount >= REBUILD_THRESHOLD) {
                // 极短时间内大量空轮询 → 触发 bug，重建 Selector
                LOGGER.warn("Selector empty poll detected ({} times), rebuilding...", emptySelectCount);
                rebuildSelector();
                // 重建后重置起始时间和计数器
                startTimeNanos = System.nanoTime();
                emptySelectCount = 0;
            }
        }
    }

    // ==================== Selector 重建 ====================

    /**
     * 重建 Selector 以解决 epoll 空轮询 bug。
     * <p>
     * 创建新的 Selector，将旧 Selector 上的所有通道迁移过去，然后关闭旧 Selector。
     * </p>
     */
    private void rebuildSelector() {
        final Selector oldSelector = delegate;
        Selector newSelector;
        try {
            newSelector = Selector.open();
        } catch (IOException e) {
            LOGGER.warn("Failed to create new Selector", e);
            return;
        }

        // 迁移所有通道到新 Selector
        int migratedCount = 0;
        for (SelectionKey key : oldSelector.keys()) {
            try {
                if (!key.isValid() || key.channel().keyFor(newSelector) != null) {
                    continue;
                }
                int interestOps = key.interestOps();
                Object attachment = key.attachment();
                key.cancel();
                key.channel().register(newSelector, interestOps, attachment);
                migratedCount++;
            } catch (Exception e) {
                LOGGER.warn("Failed to migrate channel to new Selector", e);
            }
        }

        delegate = newSelector;

        try {
            oldSelector.close();
        } catch (IOException e) {
            LOGGER.warn("Failed to close old Selector", e);
        }

        LOGGER.info("Migrated {} channel(s) to new Selector", migratedCount);
    }
}
