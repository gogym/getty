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

import com.gettyio.core.channel.SocketChannel;

/**
 * ChannelPipeline默认实现
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    AbstractChannelHandlerContext head;
    AbstractChannelHandlerContext tail;
    SocketChannel socketChannel;

    public DefaultChannelPipeline(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        tail = new DefaultChannelHandlerContext(socketChannel, new DefaultChannelHandler());
        head = new DefaultChannelHandlerContext(socketChannel, new DefaultChannelHandler());
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler handler) {
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(socketChannel, handler);
        AbstractChannelHandlerContext nextCtx = this.head.next;
        newCtx.prev = this.head;
        newCtx.next = nextCtx;
        this.head.next = newCtx;
        nextCtx.prev = newCtx;
        return this;
    }

    public ChannelPipeline addLast(ChannelHandler handler) {
        AbstractChannelHandlerContext newCtx = new DefaultChannelHandlerContext(socketChannel, handler);
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
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

    @Override
    public boolean isFirst(ChannelHandler handler) {
        AbstractChannelHandlerContext first = this.head.next;
        return first.handler() == handler;
    }

    @Override
    public boolean isLast(ChannelHandler handler) {
        AbstractChannelHandlerContext last = this.tail.prev;
        return last.handler() == handler;
    }


}
