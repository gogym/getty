package com.gettyio.core.handler.codec.datagramPacket;/*
 * 类名：DatagramPacketEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/18
 */

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.PipelineDirection;

public class DatagramPacketEncoder extends MessageToByteEncoder {

    @Override
    public void encode(AioChannel aioChannel, Object obj) {
        //System.out.println("输出编码");
    }

    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        //encode(aioChannel, obj);
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }
}
