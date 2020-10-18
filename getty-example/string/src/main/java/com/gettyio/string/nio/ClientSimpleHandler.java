package com.gettyio.string.nio;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

import java.io.IOException;

public class ClientSimpleHandler extends SimpleChannelInboundHandler<String> {


    @Override
    public void channelAdded(SocketChannel aioChannel) {

    }

    @Override
    public void channelClosed(SocketChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel, String str) {
        System.out.println("读取服务器端的消息:" + str);
    }

    @Override
    public void exceptionCaught(SocketChannel aioChannel, Throwable cause) throws Exception {
        System.out.println("出错了");
    }
}
