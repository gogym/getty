/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.LinkedNonBlockQueue;

/**
 * 类名：DefaultFrameDecoder.java
 * 描述：字符串默认解码器，收到多少传回多少，中间不做任何处理
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultFrameDecoder extends ChannelInboundHandlerAdapter {

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        super.decode(socketChannel, obj, out);
    }

}
