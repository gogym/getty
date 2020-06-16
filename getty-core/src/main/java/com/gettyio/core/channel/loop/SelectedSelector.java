package com.gettyio.core.channel.loop;
/*
 * 类名：SelectorHelper
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/6/16
 */

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class SelectedSelector extends Selector {

    private volatile boolean mark = false;
    private final Selector selector;

    public SelectedSelector(Selector selector) {
        this.selector = selector;
    }

    public Selector getSelector() {
        return selector;
    }

    /**
     * 同步， 保证多个线程调用register的时候不会出现问题
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
        return selector.select(timeout);
    }

    @Override
    public int select() throws IOException {
        for (; ; ) {
            if (mark == true) {
                continue;
            }
            int select = selector.select();
            if (select >= 1) {
                return select;
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
}

