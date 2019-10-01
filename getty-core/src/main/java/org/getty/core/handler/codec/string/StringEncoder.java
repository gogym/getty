/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.codec.string;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.out.ChannelOutboundHandlerAdapter;

/**
 * 类名：StringEncoder.java
 * 描述：字符encode编码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class StringEncoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void encode(AioChannel aioChannel, byte[] bytes) {
        //System.out.println("输出编码");
    }


    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        encode(aioChannel, bytes);
        super.handler(channelStateEnum, bytes, cause, aioChannel, pipelineDirection);
    }


}
