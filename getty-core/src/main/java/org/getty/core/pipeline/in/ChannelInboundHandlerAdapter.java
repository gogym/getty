/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline.in;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.handler.timeout.IdleState;
import org.getty.core.pipeline.ChannelHandlerAdapter;
import org.getty.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import org.getty.core.pipeline.PipelineDirection;

/**
 * 类名：ChannelInboundHandlerAdapter.java
 * 描述：入栈器父类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler {

    @Override
    public void channelAdded(AioChannel aioChannel) {
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
    public void channelClosed(AioChannel aioChannel) {
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
    public void channelRead(AioChannel aioChannel, Object obj) {
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
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
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
    public void decode(AioChannel aioChannel, byte[] bytes) {
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            ((ChannelInboundHandlerAdapter) channelHandlerAdapter).decode(aioChannel, bytes);
        }
    }


    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        //把任务传递给下一个处理器
        ChannelHandlerAdapter channelHandlerAdapter = this;
        while (true) {
            channelHandlerAdapter = aioChannel.getDefaultChannelPipeline().nextOne(channelHandlerAdapter);
            if (channelHandlerAdapter == null || channelHandlerAdapter instanceof ChannelInboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                break;
            }
        }
        if (channelHandlerAdapter != null) {
            channelHandlerAdapter.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
        }

    }

    @Override
    public void userEventTriggered(AioChannel aioChannel, IdleState evt) {
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
