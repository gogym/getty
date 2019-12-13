/**
 * 包名：org.getty.core.channel.group
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.group;

import com.gettyio.core.channel.AioChannel;

import java.util.Set;

/**
 * 类名：ChannelGroup.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
public interface ChannelGroup extends Set<AioChannel>, Comparable<ChannelGroup> {

    String name();
    AioChannel find(String id);
}
