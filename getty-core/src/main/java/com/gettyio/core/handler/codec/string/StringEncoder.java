/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.PipelineDirection;

/**
 * 类名：StringEncoder.java
 * 描述：字符encode编码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class StringEncoder extends MessageToByteEncoder {

    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {
        //System.out.println("输出编码");
    }


    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) throws Exception {
        //encode(aioChannel, bytes);
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }


}
