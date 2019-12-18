/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 类名：DefaultFrameDecoder.java
 * 描述：字符串默认解码器，收到多少传回多少，中间不做任何处理
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultFrameDecoder extends ChannelInboundHandlerAdapter {

    @Override
    public void decode(AioChannel aioChannel, Object obj) {
        //传递到下一个decode
        super.decode(aioChannel, obj);
    }

    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (null != obj && aioChannel instanceof TcpChannel) {
            decode(aioChannel, obj);
        }
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }
}
