package com.gettyio.core.handler.codec.protobuf;/*
 * 类名：ProtobufEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/8
 */

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.PipelineDirection;

/**
 * 类名：ProtobufEncoder.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/9
 */
public class ProtobufEncoder extends MessageToByteEncoder {
    @Override
    public void encode(AioChannel aioChannel, byte[] bytes) {

    }

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        super.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
    }
}
