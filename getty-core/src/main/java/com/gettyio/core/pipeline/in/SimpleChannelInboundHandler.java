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


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.DatagramPacketHandler;
import com.gettyio.core.util.LinkedNonReadBlockQueue;

/**
 * SimpleChannelInboundHandler.java
 *
 * @description:简易的通道输出，继承这个类可容易实现消息接收
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public abstract class SimpleChannelInboundHandler<T> extends ChannelInboundHandlerAdapter implements DatagramPacketHandler {


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {

        while (out.getCount() > 0) {
            channelRead(socketChannel, out.poll());
        }

    }


    @Override
    public void channelRead(SocketChannel socketChannel, Object obj) throws Exception {
        channelRead0(socketChannel, (T) obj);
    }


    /**
     * 解码后的消息输出
     *
     * @param socketChannel 通道
     * @param t             解密后的消息
     * @throws Exception 异常
     */
    public abstract void channelRead0(SocketChannel socketChannel, T t) throws Exception;

}
