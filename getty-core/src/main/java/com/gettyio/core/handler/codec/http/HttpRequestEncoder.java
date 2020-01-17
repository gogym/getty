package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpRequestEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;

public class HttpRequestEncoder extends MessageToByteEncoder {

    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {


        AutoByteBuffer buffer = AutoByteBuffer.newByteBuffer();
        if (obj instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) obj;
            HttpEncodeSerializer.encodeInitialLine(buffer, httpRequest);
            HttpEncodeSerializer.encodeHeaders(buffer, httpRequest);
            HttpEncodeSerializer.encodeContent(buffer, httpRequest);
            obj = buffer.readableBytesArray();
        }
        super.encode(aioChannel, obj);
    }
}
