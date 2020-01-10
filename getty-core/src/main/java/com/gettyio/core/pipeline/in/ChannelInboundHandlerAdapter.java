/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.util.LinkedNonBlockQueue;


/**
 * 类名：ChannelInboundHandlerAdapter.java
 * 描述：入栈器父类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler {

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
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextInPipe(this);
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
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
