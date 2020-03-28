/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.util.LinkedNonBlockQueue;

/**
 * 类名：ChannelHandlerAdapter.java
 * 描述：handler 抽像父类，in out父类需继承该类
 * 修改人：gogym
 * 时间：2019/9/27
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
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
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
