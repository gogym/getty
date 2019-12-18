/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.timeout.IdleState;

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
     * @param channelStateEnum  数据方向
     * @param obj             数据
     * @param aioChannel        通道
     * @param pipelineDirection 责任链
     */
    public abstract void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection);

    /**
     * 该方法类似一个心态起搏器，执行读或写操作会被触发
     *
     * @param aioChannel 通道
     * @param evt        IdleState
     */
    public abstract void userEventTriggered(AioChannel aioChannel, IdleState evt);

}
