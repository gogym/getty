/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com 
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline;


import org.getty.core.channel.AioChannel;

public abstract class ChannelInitializer implements ChannelPipeline {

    public abstract void initChannel(AioChannel aioChannel) throws Exception;

}
