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
package com.gettyio.core.pipeline.in;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;

/**
 * 简单入站消息处理器。
 * <p>
 * 提供泛型化的消息处理方法 {@link #channelRead0(AbstractSocketChannel, Object)}，
 * 子类只需指定消息类型并实现此方法即可，无需手动类型转换。
 * 适用于只需处理入站消息的简单业务场景。
 * </p>
 *
 * <p><b>注意：</b>{@link #channelRead0} 不自动将消息传播到下一个处理器，
 * 如需继续传播，应手动调用 {@code ctx.fireChannelProcess(CHANNEL_READ, msg)}。</p>
 *
 * @param <T> 消息类型
 */
public abstract class SimpleChannelInboundHandler<T> extends ChannelInboundHandlerAdapter {

    /**
     * 拦截入站消息并转换为指定类型后调用 {@link #channelRead0}。
     *
     * @param ctx 通道上下文
     * @param in  原始入站消息
     * @throws Exception 处理过程中发生错误时抛出
     */
    @Override
    @SuppressWarnings("unchecked")
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        channelRead0(ctx.channel(), (T) in);
    }

    /**
     * 处理已解码的消息。子类实现此方法以处理业务逻辑。
     *
     * @param channel 底层通道，可用于发送响应
     * @param msg     已转换类型的消息
     * @throws Exception 处理过程中发生错误时抛出
     */
    public abstract void channelRead0(AbstractSocketChannel channel, T msg) throws Exception;
}
