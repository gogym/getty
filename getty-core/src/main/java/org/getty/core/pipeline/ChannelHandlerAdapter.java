/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline;


import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.handler.timeout.IdleState;

/**
 * 类名：ChannelHandlerAdapter.java
 * 描述：handler 抽像父类，in out父类需继承该类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelHandlerAdapter implements ChannelboundHandler {

    /**
     * 实现这个方法，用于处理器传递
     *
     * @param channelStateEnum
     * @param bytes
     * @param cause
     * @param aioChannel
     */
    public abstract void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection);


    public abstract void userEventTriggered(AioChannel aioChannel, IdleState evt);

}
