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
 * ChannelHandlerAdapter.java
 *
 * @description: handler 处理器抽像父类
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public abstract class ChannelHandlerAdapter implements ChannelBoundHandler {

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
     * 处理不同状态下的通道事件
     *
     * @param ctx 通道的上下文对象，用于访问通道及其配置
     * @param channelState 通道的状态，用于确定要执行的操作
     * @param in 事件的具体内容，类型取决于通道的状态
     * @throws Exception 如果在处理过程中发生错误，则抛出异常
     */
    @Override
    public void channelProcess(ChannelHandlerContext ctx, ChannelState channelState, Object in) throws Exception {

        // 根据通道状态执行相应的操作
        switch (channelState) {
            case NEW_CHANNEL: // 当通道被添加时
                channelAdded(ctx);
                break;
            case CHANNEL_CLOSED: // 当通道关闭时
                channelClosed(ctx);
                break;
            case CHANNEL_READ: // 当通道有数据可读时
                channelRead(ctx, in);
                break;
            case CHANNEL_WRITE: // 当通道有数据待写入时
                channelWrite(ctx, in);
                break;
            case CHANNEL_EVENT: // 当用户事件触发时，例如空闲状态改变
                userEventTriggered(ctx, (IdleState) in);
                break;
            case CHANNEL_EXCEPTION: // 当通道发生异常时
                exceptionCaught(ctx, new RuntimeException(channelState.name()));
                break;
            default: // 默认情况下，不做任何操作
        }
    }

    //---------------------------------------------------


    @Override
    public void channelAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelProcess(ChannelState.NEW_CHANNEL, null);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_CLOSED, null);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_READ, in);
    }

    /**
     * 重写channelWrite方法，以处理数据写入逻辑
     * 本方法的目的是确保数据能够根据处理器链的位置，被正确地处理或传输
     * 如果当前处理器是第一个处理器，则直接将数据写入通道并传输
     * 否则，通过上下文通知后续处理器有数据需要处理
     *
     * @param ctx 表示当前处理器上下文，用于访问和操作当前通道及其处理器链
     * @param obj 待写入通道的对象，可以是任何实现了Serializable接口的对象
     * @throws Exception 如果在处理过程中发生错误，则会抛出异常
     */
    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        // 检查当前处理器是否是通道的第一个处理器
        boolean isFirst = ctx.channel().getChannelPipeline().isFirst(this);
        if (isFirst) {
            // 注意，encode是在输出链，如果当前处理器是第一个处理器，直接将数据写入通道并传输
            ctx.channel().writeToChannel(obj);
        } else {
            // 如果当前处理器不是第一个处理器，通过上下文通知后续处理器有数据需要处理
            ctx.fireChannelProcess(ChannelState.CHANNEL_WRITE, obj);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_EXCEPTION, cause);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) throws Exception {
        ctx.fireChannelProcess(ChannelState.CHANNEL_EVENT, evt);
    }

}
