/**
 * 包名：org.getty.core.channel.group
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.group;

import org.getty.core.channel.AioChannel;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 类名：DefaultChannelGroup.java
 * 描述： 用于保存连接，方便查找
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultChannelGroup extends AbstractSet<AioChannel> implements
        ChannelGroup {

    private final String name;
    //用于保存连接的map
    private final ConcurrentMap<String, AioChannel> serverChannels = new ConcurrentHashMap<>();


    public DefaultChannelGroup() {
        this.name = "defaultChannelGroup";
    }

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
    public AioChannel find(String id) {
        AioChannel aioChannel = serverChannels.get(id);
        if (aioChannel != null) {
            return aioChannel;
        }
        return null;
    }


    @Override
    public boolean isEmpty() {
        return serverChannels.isEmpty();
    }


    @Override
    public boolean contains(Object o) {
        if (o instanceof AioChannel) {
            return serverChannels.containsValue(o);
        }
        return false;
    }

    @Override
    public boolean add(AioChannel aioChannel) {
        boolean added = serverChannels.putIfAbsent(aioChannel.getChannelId(), aioChannel) == null;
        if (added) {
            //这里要添加个关闭监听，当连接关闭时，自动清理
            aioChannel.setChannelFutureListener(remover);
        }
        return added;
    }


    @Override
    public boolean remove(Object o) {
        AioChannel c = null;
        if (o instanceof String) {
            c = serverChannels.remove(o);
        } else if (o instanceof AioChannel) {
            c = (AioChannel) o;
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
        Collection<AioChannel> channels = new ArrayList<AioChannel>(size());
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
    public Iterator<AioChannel> iterator() {
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
    private final ChannelFutureListener remover = aioChannel -> remove(aioChannel);

}
