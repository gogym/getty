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

import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.constant.IdleState;

/**
 * 通道处理器适配器。
 * <p>
 * 提供 {@link ChannelBoundHandler} 所有方法的默认实现，采用"透传"策略：
 * 将事件直接传播到管道中的下一个处理器，不做任何业务处理。
 * </p>
 *
 * <p>用户自定义处理器通常继承此类，并只覆盖感兴趣的事件方法，无需实现所有接口方法。</p>
 *
 * <p><b>写路径说明：</b>{@link #channelWrite} 的默认实现直接调用
 * {@code ctx.fireChannelProcess(CHANNEL_WRITE, obj)}，由框架层面
 * （{@link AbstractChannelHandlerContext#fireChannelProcess}）负责判断是否到达头节点
 * 并写入底层通道，避免了每次写操作的 {@code isFirst()} 链表遍历开销。</p>
 */
public abstract class ChannelHandlerAdapter implements ChannelBoundHandler {

    /** 当前处理器绑定的上下文引用 */
    private ChannelHandlerContext channelHandlerContext;

    @Override
    public ChannelHandlerContext channelHandlerContext() {
        return channelHandlerContext;
    }

    @Override
    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        this.channelHandlerContext = ctx;
    }

    /**
     * 根据通道状态分发事件到对应的处理方法。
     * <p>
     * 该方法由框架调用，将通用的 channelProcess 分发为具体的事件回调方法，
     * 使子类只需覆盖感兴趣的特定事件方法。
     * </p>
     *
     * @param ctx          当前处理器上下文
     * @param channelState 通道状态
     * @param in           事件数据
     * @throws Exception 处理过程中发生错误时抛出
     */
    @Override
    public void channelProcess(ChannelHandlerContext ctx, ChannelState channelState, Object in) throws Exception {
        switch (channelState) {
            case NEW_CHANNEL:
                channelAdded(ctx);
                break;
            case CHANNEL_CLOSED:
                channelClosed(ctx);
                break;
            case CHANNEL_READ:
                channelRead(ctx, in);
                break;
            case CHANNEL_WRITE:
                channelWrite(ctx, in);
                break;
            case CHANNEL_EVENT:
                userEventTriggered(ctx, (IdleState) in);
                break;
            case CHANNEL_EXCEPTION:
                exceptionCaught(ctx, (Throwable) in);
                break;
            default:
                // 未知状态，忽略
        }
    }

    /**
     * 通道加入事件默认实现：直接向后传播。
     */
    @Override
    public void channelAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelProcess(ChannelState.NEW_CHANNEL, null);
    }

    /**
     * 通道关闭事件默认实现：直接向后传播。
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_CLOSED, null);
    }

    /**
     * 通道读取事件默认实现：直接向后传播。
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_READ, in);
    }

    /**
     * 通道写入事件默认实现：直接向前传播。
     * <p>
     * 框架会在 {@link AbstractChannelHandlerContext#fireChannelProcess} 中自动判断
     * 是否到达头节点，并在头节点处直接写入底层通道，无需在此处调用 isFirst()。
     * </p>
     */
    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_WRITE, obj);
    }

    /**
     * 异常捕获事件默认实现：直接向后传播。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_EXCEPTION, cause);
    }

    /**
     * 心跳事件默认实现：直接向后传播。
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_EVENT, evt);
    }
}
