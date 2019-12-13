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
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;


/**
 * 类名：FixedLengthFrameDecoder.java
 * 描述：字符串定长消息解码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class FixedLengthFrameDecoder extends ChannelInboundHandlerAdapter {

    private int frameLength;

    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength must be a positive integer: " + frameLength);
        } else {
            this.frameLength = frameLength;
        }
    }

    public void decode(AioChannel aioChannel, byte[] bytes) {

        int index = 0;
        while (index < bytes.length) {
            byte[] byte2;
            if ((bytes.length - index) > frameLength) {
                byte2 = new byte[frameLength];
                System.arraycopy(bytes, index, byte2, 0, frameLength);
            } else {
                byte2 = new byte[bytes.length - index];
                System.arraycopy(bytes, index, byte2, 0, bytes.length - index);
            }
            //传递到下一个解码器
            super.decode(aioChannel, byte2);
            index += frameLength;
        }


    }

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (null != bytes) {
            decode(aioChannel, bytes);
        }
        super.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
    }
}
