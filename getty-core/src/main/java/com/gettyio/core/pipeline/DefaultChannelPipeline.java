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
 * ChannelPipeline默认实现
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    AbstractChannelHandlerContext head;
    AbstractChannelHandlerContext tail;
    AbstractSocketChannel abstractSocketChannel;

    /**
     * 构造函数，用于初始化DefaultChannelPipeline对象
     * @param abstractSocketChannel 传入的SocketChannel对象，用于建立连接和通信
     */
    public DefaultChannelPipeline(AbstractSocketChannel abstractSocketChannel) {
        this.abstractSocketChannel = abstractSocketChannel;
        // 创建尾部DefaultChannelHandlerContext，用于管理连接的读写操作
        tail = new DefaultChannelHandlerContext(abstractSocketChannel, new DefaultChannelHandler());
        // 创建头部DefaultChannelHandlerContext，用于作为链表的起始点，管理整个处理器链
        head = new DefaultChannelHandlerContext(abstractSocketChannel, new DefaultChannelHandler());
        // 初始化链表，将头部的下一个元素设置为尾部，尾部的上一个元素设置为头部，形成一个环形链表结构
        head.next = tail;
        tail.prev = head;
    }

    /**
     * 在ChannelPipeline的头部添加一个新的处理器
     *
     * @param handler 要添加的ChannelHandler实例
     * @return ChannelPipeline实例，支持链式调用
     */
    @Override
    public ChannelPipeline addFirst(ChannelHandler handler) {
        // 创建一个新的DefaultChannelHandlerContext实例，用于包装传入的handler
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(abstractSocketChannel, handler);

        // 获取当前head节点的下一个上下文，用于后续的链表链接操作
        AbstractChannelHandlerContext nextCtx = this.head.next;

        // 将新创建的上下文节点链接到当前链表中，实现插入操作
        newCtx.prev = this.head;
        newCtx.next = nextCtx;
        this.head.next = newCtx;
        nextCtx.prev = newCtx;

        // 返回当前的ChannelPipeline实例，支持链式调用
        return this;
    }


    /**
     * 在处理链中添加一个新的处理器到末尾
     *
     * @param handler 要添加的处理器
     * @return 返回更新后的处理链
     */
    public ChannelPipeline addLast(ChannelHandler handler) {
        // 创建一个新的上下文，将处理器包装在内
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(abstractSocketChannel, handler);
        // 找到当前链尾部的前一个上下文
        AbstractChannelHandlerContext prev = tail.prev;
        // 将新上下文链接到链的末尾，更新前后引用
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
        // 返回更新后的处理链
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

    /**
     * 判断指定的ChannelHandler是否是链表中的第一个处理器
     * 此方法重写了父类的相应方法，以确保在当前实现中正确判断处理器的位置
     *
     * @param handler 待判断的ChannelHandler对象
     * @return 如果指定的ChannelHandler是链表中的第一个处理器，则返回true；否则返回false
     */
    @Override
    public boolean isFirst(ChannelHandler handler) {
        // 访问链表中第一个上下文对象
        AbstractChannelHandlerContext first = this.head.next;
        // 判断给定的处理器是否与链表中第一个处理器相同
        return first.handler() == handler;
    }


    /**
     * 判断指定的ChannelHandler是否是链表中的最后一个处理器
     * 此方法通过检查给定的ChannelHandler是否与内部维护的“尾部前一个”处理器相同来实现判断
     *
     * @param handler 要判断的ChannelHandler对象
     * @return 如果指定的ChannelHandler是链表中的最后一个处理器，则返回true；否则返回false
     */
    @Override
    public boolean isLast(ChannelHandler handler) {
        // 获取链表中最后一个处理器的上下文
        AbstractChannelHandlerContext last = this.tail.prev;
        // 比较传入的处理器与链表中最后一个处理器是否为同一个对象
        return last.handler() == handler;
    }



}
