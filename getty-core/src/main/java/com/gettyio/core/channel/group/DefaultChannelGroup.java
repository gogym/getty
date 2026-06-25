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
package com.gettyio.core.channel.group;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.util.ConcurrentSafeMap;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * ChannelGroup 默认实现。
 * <p>
 * 内部使用 {@link ConcurrentSafeMap} 存储通道（以通道 ID 为 key），
 * 支持按 ID 快速查找。添加通道时自动注册关闭监听器，
 * 通道关闭后自动从组中移除。
 * </p>
 *
 * @author gogym
 */
public class DefaultChannelGroup extends AbstractSet<AbstractSocketChannel> implements ChannelGroup {

    /** 组名称 */
    private final String name;

    /** 通道存储（channelId → channel） */
    private final ConcurrentSafeMap<String, AbstractSocketChannel> channels = new ConcurrentSafeMap<>();

    /**
     * 默认构造（组名为 "default"）。
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
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AbstractSocketChannel find(String id) {
        return channels.get(id);
    }

    @Override
    public boolean isEmpty() {
        return channels.isEmpty();
    }

    @Override
    public int size() {
        return channels.size();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof AbstractSocketChannel) {
            return channels.containsValue(o);
        }
        return false;
    }

    @Override
    public boolean add(AbstractSocketChannel channel) {
        boolean added = channels.putIfAbsent(channel.getChannelId(), channel) == null;
        if (added) {
            // 注册关闭监听器，通道关闭时自动从组中移除
            channel.setChannelFutureListener(autoRemover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        AbstractSocketChannel removed = null;
        if (o instanceof String) {
            removed = channels.remove(o);
        } else if (o instanceof AbstractSocketChannel) {
            removed = channels.remove(((AbstractSocketChannel) o).getChannelId());
        }
        if (removed == null) {
            return false;
        }
        removed.setChannelFutureListener(null);
        return true;
    }

    @Override
    public void clear() {
        channels.clear();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Collection<AbstractSocketChannel> snapshot = new ArrayList<>(size());
        snapshot.addAll(channels.values());
        return snapshot.toArray(a);
    }

    @Override
    public Iterator<AbstractSocketChannel> iterator() {
        return channels.values().iterator();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(ChannelGroup o) {
        int cmp = name().compareTo(o.name());
        return cmp != 0 ? cmp : System.identityHashCode(this) - System.identityHashCode(o);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(name: " + name() + ", size: " + size() + ')';
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
