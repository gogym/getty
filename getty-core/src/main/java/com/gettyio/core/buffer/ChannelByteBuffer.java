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
package com.gettyio.core.buffer;

import com.gettyio.core.channel.SocketChannel;

import java.nio.ByteBuffer;


/**
 * 通道与待输出缓冲区包装
 *
 * @className ChannelByteBuffer.java
 * @description:
 * @author:gogym
 * @date:2020/6/17
 */
public class ChannelByteBuffer {

    /**
     * socket channel
     */
    private SocketChannel socketChannel;

    /**
     * 数据缓冲
     */
    private ByteBuffer byteBuffer;


    public ChannelByteBuffer(SocketChannel socketChannel, ByteBuffer byteBuffer) {
        this.socketChannel = socketChannel;
        this.byteBuffer = byteBuffer;
    }


    public void set(SocketChannel socketChannel, ByteBuffer byteBuffer) {
        this.socketChannel = socketChannel;
        this.byteBuffer = byteBuffer;
    }

    public SocketChannel getNioChannel() {
        return socketChannel;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }
}
