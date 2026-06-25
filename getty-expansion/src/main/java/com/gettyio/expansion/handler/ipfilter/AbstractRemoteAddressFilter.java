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
 * 抽象远程地址过滤器。
 * <p>
 * 在通道加入管道时（{@link #channelAdded}）自动提取远程地址并调用 {@link #accept} 方法，
 * 由子类决定是否允许该地址通过。若不允许或地址为 null，则关闭连接。
 * </p>
 *
 * @param <T> SocketAddress 的子类型
 * @author gogym
 */
abstract class AbstractRemoteAddressFilter<T extends SocketAddress> extends ChannelInboundHandlerAdapter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AbstractRemoteAddressFilter.class);

    @Override
    public void channelAdded(ChannelHandlerContext ctx) throws Exception {
        super.channelAdded(ctx);
        handleNewChannel(ctx.channel());
    }

    /**
     * 检查新通道的远程地址是否符合过滤规则。
     */
    @SuppressWarnings("unchecked")
    private void handleNewChannel(AbstractSocketChannel abstractSocketChannel) {
        try {
            T remoteAddress = (T) abstractSocketChannel.getRemoteAddress();
            if (remoteAddress == null) {
                abstractSocketChannel.close();
                return;
            }
            if (!accept(abstractSocketChannel, remoteAddress)) {
                abstractSocketChannel.close();
            }
        } catch (IOException e) {
            LOGGER.error("filter remote address failed", e);
        }
    }

    /**
     * 判断是否允许指定远程地址的通道通过。
     *
     * @param abstractSocketChannel 通道
     * @param remoteAddress         远程地址
     * @return true 表示允许，false 表示拒绝（将关闭连接）
     */
    protected abstract boolean accept(AbstractSocketChannel abstractSocketChannel, T remoteAddress);
}
