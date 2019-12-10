package org.getty.core.handler.codec.protobuf;/*
 * 类名：ProtobufEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/8
 */

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.handler.codec.MessageToByteEncoder;
import org.getty.core.pipeline.PipelineDirection;

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
