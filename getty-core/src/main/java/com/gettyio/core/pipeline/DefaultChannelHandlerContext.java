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
import com.gettyio.core.channel.AbstractSocketChannel;

/**
 * DefaultChannelHandlerContext默认实现
 */
class DefaultChannelHandlerContext extends AbstractChannelHandlerContext implements ChannelHandler {

    private final ChannelHandler handler;

    private final AbstractSocketChannel abstractSocketChannel;

    DefaultChannelHandlerContext(AbstractSocketChannel abstractSocketChannel, ChannelHandler handler) {
        this.handler = handler;
        this.abstractSocketChannel = abstractSocketChannel;
        setChannelHandlerContext(this);
    }

    @Override
    public AbstractSocketChannel channel() {
        return abstractSocketChannel;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    @Override
    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        this.handler.setChannelHandlerContext(this);
    }

    @Override
    public ChannelHandlerContext channelHandlerContext() {
        return this;
    }

    @Override
    public void channelProcess(ChannelHandlerContext ctx, ChannelState channelState, Object in) throws Exception {
        ctx.fireChannelProcess(channelState, in);
    }

}
