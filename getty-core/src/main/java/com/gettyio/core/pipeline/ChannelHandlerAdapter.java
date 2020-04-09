/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.util.LinkedNonReadBlockQueue;


/**
 * ChannelHandlerAdapter.java
 *
 * @description:handler 抽像父类，in out父类需继承该类
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public abstract class ChannelHandlerAdapter implements ChannelboundHandler {

    @Override
    public void channelAdded(SocketChannel socketChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelAdded(socketChannel);
    }

    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelClosed(socketChannel);
    }

    @Override
    public void channelRead(SocketChannel socketChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelRead(socketChannel, obj);
    }

    @Override
    public void exceptionCaught(SocketChannel socketChannel, Throwable cause) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.exceptionCaught(socketChannel, cause);
    }


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.decode(socketChannel, obj, out);
    }


    //------------------------------------------------

    @Override
    public void channelWrite(SocketChannel socketChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelWrite(socketChannel, obj);
    }

    @Override
    public void encode(SocketChannel socketChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            socketChannel.writeToChannel(obj);
            return;
        }
        channelHandlerAdapter.encode(socketChannel, obj);
    }


    //-------------------------------------------------------------

    @Override
    public void userEventTriggered(SocketChannel socketChannel, IdleState evt) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = socketChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(socketChannel, evt);
        }
    }

}
