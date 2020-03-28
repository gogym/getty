/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.LinkedNonBlockQueue;

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
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        byte[] bytes = (byte[]) obj;
        int index = 0;
        while (index < bytes.length) {
            byte data = bytes[index];
            if (data != endFLag[exceptIndex]) {
                preBuffer.writeByte(data);
                exceptIndex = 0;
            } else if (++exceptIndex == endFLag.length) {
                //传递到下一个解码器
                super.decode(socketChannel, preBuffer.array(), out);
                preBuffer.clear();
                exceptIndex = 0;
            }
            index++;
        }

    }
}
