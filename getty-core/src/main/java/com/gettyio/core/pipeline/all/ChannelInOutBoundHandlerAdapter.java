/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.all;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 类名：ChannelInOutBoundHandlerAdapter.java
 * 描述：需双向执行的责任链继承这个类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelInOutBoundHandlerAdapter extends ChannelHandlerAdapter {

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        //需要先判断数据流向
        ChannelHandlerAdapter channelHandlerAdapter = this;
        if (pipelineDirection == PipelineDirection.IN) {
            while (true) {
                channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
                if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                    break;
                }
            }
        } else {
            while (true) {
                channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(channelHandlerAdapter);
                if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
                    break;
                }
            }
        }

        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
        } else {
            //当输出责任链已经走完，如果方向是out.则需要写到通道里
            if (pipelineDirection == PipelineDirection.OUT) {
                aioChannel.writeToChannel(bytes);
            }
        }
    }


    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        if (evt == IdleState.READER_IDLE) {
            while (true) {
                channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
                if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                    break;
                }
            }
        } else {
            while (true) {
                channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(channelHandlerAdapter);
                if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
                    break;
                }
            }
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.userEventTriggered(aioChannel, evt);
        }
    }


    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        if (pipelineDirection == PipelineDirection.IN) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);

        } else {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(channelHandlerAdapter);
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().lastOne(channelHandlerAdapter);
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.exceptionCaught(aioChannel, cause, pipelineDirection);
        }
    }
}
