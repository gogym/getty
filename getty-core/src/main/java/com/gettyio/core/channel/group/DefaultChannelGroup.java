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
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.channel.group;

import com.gettyio.core.channel.AbstractSocketChannel;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChannelGroup 默认实现。
 * <p>
 * 内部使用 {@link ConcurrentHashMap} 存储通道（以通道 ID 为 key），
 * 读操作完全无锁（volatile read），广播遍历性能最优。
 * 添加通道时自动注册关闭监听器，通道关闭后自动从组中移除。
 * </p>
 *
 * @author gogym
 */
public class DefaultChannelGroup extends AbstractSet<AbstractSocketChannel> implements ChannelGroup {

    /** 组名称 */
    private final String name;

    /** 通道存储（channelId → channel），无锁读 */
    private final ConcurrentHashMap<String, AbstractSocketChannel> channels;

    /**
     * 默认构造（组名为 "defaultChannelGroup"）。
     */
    public DefaultChannelGroup() {
        this("defaultChannelGroup");
    }

    /**
     * 指定组名构造。
     *
     * @param name 组名称
     */
    public DefaultChannelGroup(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
        this.channels = new ConcurrentHashMap<>();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public AbstractSocketChannel find(String id) {
        return channels.get(id);
    }

    @Override
    public final boolean isEmpty() {
        return channels.isEmpty();
    }

    @Override
    public final int size() {
        return channels.size();
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof AbstractSocketChannel)) {
            return false;
        }
        AbstractSocketChannel ch = (AbstractSocketChannel) o;
        return ch == channels.get(ch.getChannelId());
    }

    @Override
    public boolean add(AbstractSocketChannel channel) {
        AbstractSocketChannel old = channels.putIfAbsent(channel.getChannelId(), channel);
        if (old != null) {
            return false;
        }
        // 注册关闭监听器，通道关闭时自动从组中移除
        channel.addChannelFutureListener(autoRemover);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        AbstractSocketChannel removed;
        if (o instanceof AbstractSocketChannel) {
            removed = channels.remove(((AbstractSocketChannel) o).getChannelId());
        } else if (o instanceof String) {
            removed = channels.remove((String) o);
        } else {
            return false;
        }
        if (removed == null) {
            return false;
        }
        removed.removeChannelFutureListener(autoRemover);
        return true;
    }

    @Override
    public void clear() {
        // 先清理每个通道的监听器引用，再批量移除
        for (AbstractSocketChannel ch : channels.values()) {
            ch.removeChannelFutureListener(autoRemover);
        }
        channels.clear();
    }

    @Override
    public Object[] toArray() {
        return channels.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return channels.values().toArray(a);
    }

    /**
     * 返回通道集合的迭代器。
     * <p>
     * 直接委托给 {@link ConcurrentHashMap#values()} 的迭代器，
     * 弱一致性（不抛 ConcurrentModificationException），零分配开销。
     * </p>
     */
    @Override
    public Iterator<AbstractSocketChannel> iterator() {
        return channels.values().iterator();
    }

    @Override
    public void writeToAll(Object msg) {
        for (AbstractSocketChannel ch : channels.values()) {
            try {
                ch.writeAndFlush(msg);
            } catch (Exception e) {
                // 单个通道失败不影响其他通道
            }
        }
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ChannelGroup)) {
            return false;
        }
        // ChannelGroup 按名称唯一标识
        return name.equals(((ChannelGroup) o).name());
    }

    @Override
    public int compareTo(ChannelGroup o) {
        if (o == this) {
            return 0;
        }
        int cmp = name.compareTo(o.name());
        if (cmp != 0) {
            return cmp;
        }
        // 同名不同实例：用 identityHashCode 区分，避免溢出
        int h1 = System.identityHashCode(this);
        int h2 = System.identityHashCode(o);
        return Integer.compare(h1, h2);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(name: " + name + ", size: " + size() + ')';
    }

    /**
     * 自动移除监听器：当通道关闭时自动从组中移除。
     */
    private final ChannelFutureListener autoRemover = new ChannelFutureListener() {
        @Override
        public void operationComplete(AbstractSocketChannel channel) {
            DefaultChannelGroup.this.remove(channel);
        }
    };
}
