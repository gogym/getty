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
package com.gettyio.expansion.handler.ipfilter;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.net.SocketAddress;


/**
 * AbstractRemoteAddressFilter.java
 *
 * @description:抽象IP过滤器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
abstract class AbstractRemoteAddressFilter<T extends SocketAddress> extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractRemoteAddressFilter.class);

    @Override
    public void channelAdded(ChannelHandlerContext ctx) throws Exception {
        super.channelAdded(ctx);
        handleNewChannel(ctx.channel());
    }

    /**
     * 执行IP验证
     *
     * @return void
     * @params [aioChannel]
     */
    private void handleNewChannel(AbstractSocketChannel abstractSocketChannel) {
        try {
            T remoteAddress = (T) abstractSocketChannel.getRemoteAddress();
            if (remoteAddress == null) {
                abstractSocketChannel.close();
            }
            boolean flag = accept(abstractSocketChannel, remoteAddress);
            if (!flag) {
                abstractSocketChannel.close();
            }
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * @param aioChannel    通道
     * @param remoteAddress 远程地址
     * @return Return true if connections from this IP address and port should be accepted. False otherwise.
     */
    protected abstract boolean accept(AbstractSocketChannel aioChannel, T remoteAddress);


}
