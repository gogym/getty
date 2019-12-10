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
import org.getty.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 类名：DefaultFrameDecoder.java
 * 描述：字符串默认解码器，收到多少传回多少，中间不做任何处理
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultFrameDecoder extends ChannelInboundHandlerAdapter {


    public void decode(AioChannel aioChannel, byte[] bytes) {
        //通过decode传递到read
        super.decode(aioChannel, bytes);
    }

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes,  AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (null != bytes) {
            decode(aioChannel, bytes);
        }
        super.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
    }
}
