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
package com.gettyio.core.pipeline;

import com.gettyio.core.channel.AbstractSocketChannel;

/**
 * {@link ChannelPipeline} 默认实现。
 * <p>
 * 内部维护一个以 head/tail 为哨兵的双向链表处理器链。
 * <ul>
 *   <li><b>head</b>：哨兵头节点，是入站事件的起点，其 prev 为 null</li>
 *   <li><b>tail</b>：哨兵尾节点，是出站事件的起点，其 next 为 null</li>
 * </ul>
 * 当写事件传播到 head（prev == null）时，框架自动调用
 * {@code channel().writeToChannel(obj)} 将数据写入底层通道。
 * </p>
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    /** 头哨兵节点 */
    final AbstractChannelHandlerContext head;

    /** 尾哨兵节点 */
    final AbstractChannelHandlerContext tail;

    /** 底层通道引用 */
    private final AbstractSocketChannel channel;

    /**
     * 构造管道，初始化 head/tail 哨兵并建立双向链接。
     *
     * @param channel 底层通道
     */
    public DefaultChannelPipeline(AbstractSocketChannel channel) {
        this.channel = channel;
        head = new DefaultChannelHandlerContext(channel, new DefaultChannelHandler());
        tail = new DefaultChannelHandlerContext(channel, new DefaultChannelHandler());
        head.next = tail;
        tail.prev = head;
    }

    /**
     * 在 head 之后插入处理器（管道头部）。
     *
     * @param handler 要添加的处理器
     * @return 当前管道，支持链式调用
     */
    @Override
    public ChannelPipeline addFirst(ChannelHandler handler) {
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(channel, handler);
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
        return this;
    }

    /**
     * 在 tail 之前追加处理器（管道尾部）。
     *
     * @param handler 要添加的处理器
     * @return 当前管道，支持链式调用
     */
    @Override
    public ChannelPipeline addLast(ChannelHandler handler) {
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(channel, handler);
        AbstractChannelHandlerContext prevCtx = tail.prev;
        newCtx.prev = prevCtx;
        newCtx.next = tail;
        prevCtx.next = newCtx;
        tail.prev = newCtx;
        return this;
    }

    @Override
    public ChannelHandlerContext head() {
        return head;
    }

    @Override
    public ChannelHandlerContext tail() {
        return tail;
    }
}
