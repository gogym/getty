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
 * {@link ChannelHandlerContext} 的默认实现。
 * <p>
 * 每个处理器加入管道时都会创建一个对应的上下文实例，
 * 上下文持有处理器引用和通道引用，并作为双向链表节点参与事件传播。
 * </p>
 */
class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    /** 绑定的处理器 */
    private final ChannelHandler handler;

    /** 底层通道引用 */
    private final AbstractSocketChannel channel;

    /**
     * 构造处理器上下文。
     *
     * @param channel 底层通道
     * @param handler 绑定的处理器
     */
    DefaultChannelHandlerContext(AbstractSocketChannel channel, ChannelHandler handler) {
        this.channel = channel;
        this.handler = handler;
        // 将处理器的上下文引用设置为自身（通过 handler 的 setChannelHandlerContext 方法）
        handler.setChannelHandlerContext(this);
    }

    @Override
    public AbstractSocketChannel channel() {
        return channel;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
