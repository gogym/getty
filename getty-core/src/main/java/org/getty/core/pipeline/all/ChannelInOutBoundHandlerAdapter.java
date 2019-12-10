/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline.all;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.handler.timeout.IdleState;
import org.getty.core.pipeline.ChannelHandlerAdapter;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.ChannelInboundHandlerAdapter;
import org.getty.core.pipeline.out.ChannelOutboundHandlerAdapter;

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
