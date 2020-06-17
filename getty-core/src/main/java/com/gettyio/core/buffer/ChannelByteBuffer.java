package com.gettyio.core.buffer;

import com.gettyio.core.channel.SocketChannel;

import java.nio.ByteBuffer;


/**
 * ChannelByteBuffer.java
 *
 * @description:通道与待输出缓冲区包装
 * @author:gogym
 * @date:2020/6/17
 * @copyright: Copyright by gettyio.com
 */
public class ChannelByteBuffer {

    private SocketChannel socketChannel;

    private ByteBuffer byteBuffer;

    public ChannelByteBuffer() {

    }

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
