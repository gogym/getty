/**
 * 包名：org.getty.core.pipeline.out
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.out;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.pipeline.PipelineDirection;

/**
 * 类名：ChannelOutboundHandlerAdapter.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelOutboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelOutboundHandler {


    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(this);
        if (channelHandlerAdapter != null && channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            channelHandlerAdapter.exceptionCaught(aioChannel, cause, pipelineDirection);
        }
    }


    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        //把任务传递给下一个处理器
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(this);
        if (channelHandlerAdapter != null && (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter)) {
            channelHandlerAdapter.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
        } else {
            //没有下一个处理器，表示责任链已经走完，写出
            aioChannel.writeToChannel(bytes);
        }
    }

    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) {
        ChannelHandlerAdapter channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(this);
        if (channelHandlerAdapter != null && (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter)) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }


}
