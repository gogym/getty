/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 类名：DelimiterFrameDecoder.java
 * 描述：按标识符分割消息，目前默认\r\n
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DelimiterFrameDecoder extends ChannelInboundHandlerAdapter {

    //默认分隔符
    public static byte[] lineDelimiter = new byte[]{'\r', '\n'};
    AutoByteBuffer preBuffer = AutoByteBuffer.newByteBuffer();

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

    @Override
    public void decode(AioChannel aioChannel, Object obj) throws Exception {
        byte[] bytes = (byte[]) obj;
        int index = 0;
        while (index < bytes.length) {
            byte data = bytes[index];
            if (data != endFLag[exceptIndex]) {
                preBuffer.writeByte(data);
                exceptIndex = 0;
            } else if (++exceptIndex == endFLag.length) {
                //传递到下一个解码器
                super.decode(aioChannel, preBuffer.readAllWriteBytesArray());
                preBuffer.clear();
                exceptIndex = 0;
            }
            index++;
        }
    }

    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) throws Exception {
        if (null != obj && aioChannel instanceof TcpChannel) {
            decode(aioChannel, obj);
        }
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }
}
