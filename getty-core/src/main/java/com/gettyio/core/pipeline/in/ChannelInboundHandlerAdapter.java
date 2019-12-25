/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.handler.timeout.IdleState;

/**
 * 类名：ChannelInboundHandlerAdapter.java
 * 描述：入栈器父类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler {

    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelAdded(aioChannel);
        }

    }

    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelClosed(aioChannel);
        }
    }

    @Override
    public void channelRead(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).channelRead(aioChannel, obj);
        }
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.exceptionCaught(aioChannel, cause, pipelineDirection);
        }
    }

    @Override
    public void decode(AioChannel aioChannel, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).decode(aioChannel, obj);
        }
    }


    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) throws Exception {
        //把任务传递给下一个处理器
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
        }

    }

    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }
}
