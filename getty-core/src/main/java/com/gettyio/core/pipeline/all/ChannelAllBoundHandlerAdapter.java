/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.all;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.in.ChannelInboundHandler;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandler;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import com.gettyio.core.util.LinkedBlockQueue;

/**
 * 类名：ChannelInOutBoundHandlerAdapter.java
 * 描述：需双向执行的责任链继承这个类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelAllBoundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler, ChannelOutboundHandler {


    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelAdded(aioChannel);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).channelAdded(aioChannel);
        }
    }

    @Override
    public void channelRead(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelRead(aioChannel, obj);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).channelRead(aioChannel, obj);
        }
    }

    @Override
    public void channelWrite(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            ((ChannelOutboundHandlerAdapter) channelHandlerAdapter).channelWrite(aioChannel, obj);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).channelWrite(aioChannel, obj);
        }
    }

    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelClosed(aioChannel);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).channelClosed(aioChannel);
        }
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }
        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).exceptionCaught(aioChannel, cause);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).exceptionCaught(aioChannel, cause);
        }
    }

    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        if (evt == IdleState.READER_IDLE) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(channelHandlerAdapter);
        } else {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOutPipe(channelHandlerAdapter);
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }

    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOutPipe(this);
        if (channelHandlerAdapter == null) {
            aioChannel.writeToChannel(obj);
            return;
        }
        if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).encode(aioChannel, obj);
        } else if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            ((ChannelOutboundHandlerAdapter) channelHandlerAdapter).encode(aioChannel, obj);
        }

    }

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedBlockQueue<Object> out) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter == null) {
            return;
        }

        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).decode(aioChannel, obj, out);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            ((ChannelAllBoundHandlerAdapter) channelHandlerAdapter).decode(aioChannel, obj, out);
        }
    }
}
