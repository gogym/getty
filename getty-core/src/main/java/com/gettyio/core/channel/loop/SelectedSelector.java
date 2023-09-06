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
 * 类名：SelectorHelper
 * 版权：Copyright by www.getty.com
 * 描述：
 * 时间：2020/6/16
 *
 * @author gogym
 */
public class SelectedSelector extends Selector {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SelectedSelector.class);

    /**
     * 线程同步标志
     */
    private volatile boolean mark = false;

    /**
     * 如果空轮询的次数超过了512次，就认为其触发了空轮询bug
     */
    private final int SELECTOR_AUTO_REBUILD_THRESHOLD = 512;

    /**
     * 空轮询计数器
     */
    private int selectCnt = 1;

    /**
     * 默认超时时间1s
     */
    private final long timeoutMillis = 1000;

    /**
     * 多路复用器
     */
    private Selector selector;

    /**
     * 构造方法
     *
     * @param selector
     */
    public SelectedSelector(Selector selector) {
        //super(selector.provider());
        this.selector = selector;
    }

    /**
     * 获取多路复用器
     *
     * @return
     */
    public Selector getSelector() {
        return selector;
    }

    /**
     * 注册通道监听事件
     * 需要同步， 保证多个线程调用register不会出现问题
     *
     * @param channel
     * @param op
     * @return
     * @throws ClosedChannelException
     */
    public synchronized SelectionKey register(SelectableChannel channel, int op) throws ClosedChannelException {
        mark = true;
        selector.wakeup();
        SelectionKey register = channel.register(selector, op);
        mark = false;
        return register;
    }

    public synchronized SelectionKey register(SelectableChannel channel, int op, Object att) throws ClosedChannelException {
        mark = true;
        selector.wakeup();
        SelectionKey register = channel.register(selector, op, att);
        mark = false;
        return register;
    }

    @Override
    public boolean isOpen() {
        return selector.isOpen();
    }

    @Override
    public SelectorProvider provider() {
        return selector.provider();
    }

    @Override
    public Set<SelectionKey> keys() {
        return selector.keys();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return selector.selectedKeys();
    }

    @Override
    public int selectNow() throws IOException {
        return selector.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        return select0(timeout);
    }

    @Override
    public int select() throws IOException {
        return select0(timeoutMillis);
    }

    private int select0(long timeout) throws IOException {
        //当前纳秒
        long currentTimeNanos = System.nanoTime();

        for (; ; ) {
            if (mark) {
                continue;
            }
            int select = selector.select(timeout);
            if (select >= 1) {
                return select;
            }
            //计数器+1
            selectCnt++;
            long time = System.nanoTime();
            if (time - TimeUnit.MILLISECONDS.toNanos(timeoutMillis) >= currentTimeNanos) {
                // 超时
                selectCnt = 1;
            } else if (selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                //极短的时间内轮询超过默认值512
                // 空轮询一次 cnt+1  如果一个周期内次数超过512，则假定发生了空轮询bug，重建selector
                rebuildSelector();
                selectCnt = 1;
            }
        }
    }

    @Override
    public Selector wakeup() {
        return selector.wakeup();
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }

    /**
     * 新建一个selector来解决空轮询bug
     */
    public void rebuildSelector() {
        rebuildSelector0();
    }

    /**
     * 新建一个selector来解决空轮询bug
     */
    private void rebuildSelector0() {
        final Selector oldSelector = selector;

        //新建一个selector
        Selector newSelectorTuple;
        try {
            newSelectorTuple = Selector.open();
        } catch (IOException e) {
            logger.warn("Failed to create a new Selector.", e);
            return;
        }

        // 将旧的selector的channel全部拿出来注册到新的selector上
        int nChannels = 0;
        for (SelectionKey key : oldSelector.keys()) {
            Object a = key.attachment();
            try {
                if (!key.isValid() || key.channel().keyFor(newSelectorTuple) != null) {
                    continue;
                }
                int interestOps = key.interestOps();
                key.cancel();
                SelectionKey newKey = key.channel().register(newSelectorTuple, interestOps, a);
                nChannels++;
            } catch (Exception e) {
                logger.warn("Failed to re-register a Channel to the new Selector.", e);
            }
        }

        selector = newSelectorTuple;
        //关掉旧的selector
        try {
            oldSelector.close();
        } catch (IOException e) {
            logger.warn("Failed to close the old Selector.", e);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Migrated " + nChannels + " channel(s) to the new Selector.");
        }
    }
}

