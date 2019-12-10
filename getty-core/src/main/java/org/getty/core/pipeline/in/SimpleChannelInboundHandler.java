/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
package org.getty.core.pipeline.in;


import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.PipelineDirection;

/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：简易的通道输出，继承这个类可容易实现消息接收
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class SimpleChannelInboundHandler<T> extends ChannelInboundHandlerAdapter {


    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {

        switch (channelStateEnum) {
            case NEW_CHANNEL:
                channelAdded(aioChannel);
                break;
            case CHANNEL_CLOSED:
                channelClosed(aioChannel);
                break;
            case DECODE_EXCEPTION:
                Throwable throwable = new Throwable("decode exception");
                exceptionCaught(aioChannel, throwable, pipelineDirection);
            default:
                break;
        }
        super.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
    }


    @Override
    public void channelRead(AioChannel aioChannel, Object obj) {
        channelRead0(aioChannel, (T) obj);
    }


    /**
     * 解码后的消息输出
     *
     * @return void
     * @params [aioChannel, t]
     */
    public abstract void channelRead0(AioChannel aioChannel, T t);

}
