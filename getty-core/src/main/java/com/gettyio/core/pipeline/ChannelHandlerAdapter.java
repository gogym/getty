/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import com.gettyio.core.util.LinkedNonBlockQueue;

/**
 * 类名：ChannelHandlerAdapter.java
 * 描述：handler 抽像父类，in out父类需继承该类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelHandlerAdapter implements ChannelboundHandler {

    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelAdded(aioChannel);
    }

    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelClosed(aioChannel);
    }

    @Override
    public void channelRead(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelRead(aioChannel, obj);
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.exceptionCaught(aioChannel, cause);
    }


    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.decode(aioChannel, obj, out);
    }


    //------------------------------------------------

    @Override
    public void channelWrite(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        channelHandlerAdapter.channelWrite(aioChannel, obj);
    }

    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            aioChannel.writeToChannel(obj);
            return;
        }
        channelHandlerAdapter.encode(aioChannel, obj);
    }


    //-------------------------------------------------------------

    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }

}
