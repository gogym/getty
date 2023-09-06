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
                exceptionCaught(ctx, new RuntimeException(channelState.name()));
                break;
            default:
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

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        boolean isFirst = ctx.channel().getDefaultChannelPipeline().isFirst(this);
        if (isFirst) {
            //注意，encode是在输出链。如果是最后一个处理器，要把数据输出到socket
            ctx.channel().writeToChannel(obj);
        } else {
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
