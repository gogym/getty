package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpResponseEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;

public class HttpResponseEncoder extends MessageToByteEncoder {


    @Override
    public void encode(SocketChannel socketChannel, Object obj) throws Exception {

        AutoByteBuffer buffer = AutoByteBuffer.newByteBuffer();
        if (obj instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) obj;
            HttpEncodeSerializer.encodeInitialLine(buffer, httpResponse);
            HttpEncodeSerializer.encodeHeaders(buffer, httpResponse);
            HttpEncodeSerializer.encodeContent(buffer, httpResponse);
            obj = buffer.readableBytesArray();
            //System.out.printf( new String(buffer.readableBytesArray()));
        }
        super.encode(socketChannel, obj);
    }
}
