/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.codec.string;

import org.getty.core.buffer.AutoByteBuffer;
import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 类名：DelimiterFrameDecoder.java
 * 描述：按标识符分割消息，目前默认\r\n
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DelimiterFrameDecoder extends ChannelInboundHandlerAdapter {

    //默认分隔符
    public static byte[] lineDelimiter = new byte[]{'\r', '\n'};

    /**
     * 消息结束标志
     */
    private byte[] endFLag;
    /**
     * 本次校验的结束标索引位
     */
    private int exceptIndex;

    public DelimiterFrameDecoder(byte[] endFLag) {
        this.endFLag = endFLag;
    }

    public void decode(AioChannel aioChannel, byte[] bytes) {
        int index = 0;
        AutoByteBuffer preBuffer = AutoByteBuffer.newByteBuffer();
        while (index < bytes.length) {
            byte data = bytes[index];
            if (data != endFLag[exceptIndex]) {
                preBuffer.writeByte(data);
                exceptIndex = 0;
            } else if (++exceptIndex == endFLag.length) {
                //传递到下一个解码器
                super.decode(aioChannel, bytes);
                preBuffer.clear();
                exceptIndex = 0;
            }
            index++;
        }
        preBuffer = null;
    }

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (null != bytes) {
            decode(aioChannel, bytes);
        }
        super.handler(channelStateEnum, bytes, cause, aioChannel, pipelineDirection);
    }
}
