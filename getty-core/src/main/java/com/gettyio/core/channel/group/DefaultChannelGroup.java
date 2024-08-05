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
 * DefaultChannelGroup.java
 * <p>
 * 用于保存连接默认的组，方便查找
 *
 * @author:gogym
 * @date:2020/4/8
 */
public class DefaultChannelGroup extends AbstractSet<AbstractSocketChannel> implements ChannelGroup {

    /**
     * 组名称
     */
    private final String name;

    /**
     * 用于保存连接的map
     */
    private final ConcurrentSafeMap<String, AbstractSocketChannel> serverChannels = new ConcurrentSafeMap<>();

    /**
     * 构造函数
     */
    public DefaultChannelGroup() {
        this.name = "defaultChannelGroup";
    }

    /**
     * 构造函数
     *
     * @param name
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
        AbstractSocketChannel abstractSocketChannel = serverChannels.get(id);
        if (abstractSocketChannel != null) {
            return abstractSocketChannel;
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return serverChannels.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof AbstractSocketChannel) {
            return serverChannels.containsValue(o);
        }
        return false;
    }

    @Override
    public boolean add(AbstractSocketChannel abstractSocketChannel) {
        boolean added = serverChannels.putIfAbsent(abstractSocketChannel.getChannelId(), abstractSocketChannel) == null;
        if (added) {
            //这里要添加个关闭监听，当连接关闭时，自动清理
            abstractSocketChannel.setChannelFutureListener(remover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        AbstractSocketChannel c = null;
        if (o instanceof String) {
            c = serverChannels.remove(o);
        } else if (o instanceof AbstractSocketChannel) {
            c = (AbstractSocketChannel) o;
            c = serverChannels.remove(c.getChannelId());

        }
        if (c == null) {
            return false;
        }
        //移除关闭监听
        c.setChannelFutureListener(null);
        return true;
    }

    @Override
    public void clear() {
        serverChannels.clear();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Collection<AbstractSocketChannel> channels = new ArrayList<AbstractSocketChannel>(size());
        channels.addAll(serverChannels.values());
        return channels.toArray(a);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(ChannelGroup o) {
        int v = name().compareTo(o.name());
        if (v != 0) {
            return v;
        }
        return System.identityHashCode(this) - System.identityHashCode(o);
    }

    @Override
    public Iterator<AbstractSocketChannel> iterator() {
        return serverChannels.values().iterator();
    }

    @Override
    public int size() {
        return serverChannels.size();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(name: " + name() + ", size: " + size() + ')';
    }

    /**
     * 移除监听
     */
    private final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(AbstractSocketChannel abstractSocketChannel) {
            DefaultChannelGroup.this.remove(abstractSocketChannel);
        }
    };

}
