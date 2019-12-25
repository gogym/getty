package com.gettyio.core.handler.codec.datagramPacket;/*
 * 类名：DatagramPacketDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/18
 */

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.UdpChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.pipeline.PipelineDirection;

import java.net.DatagramPacket;

public class DatagramPacketDecoder extends ObjectToMessageDecoder {

    @Override
    public void decode(AioChannel aioChannel, Object obj) throws Exception {
        try {
            DatagramPacket datagramPacket = (DatagramPacket) obj;
            //写到read通道里
            channelRead(aioChannel, datagramPacket);
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) throws Exception {
        if (null != obj && aioChannel instanceof UdpChannel) {
            decode(aioChannel, obj);
        }
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }
}
