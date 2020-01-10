package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpRequestDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonBlockQueue;

public class HttpRequestDecoder extends ObjectToMessageDecoder {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(HttpRequestDecoder.class);

    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();


    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        autoByteBuffer.writeBytes((byte[]) obj);
        HttpRequest httpRequest = new HttpRequest();
        //读取请求行
        if (!HttpSerializer.readRequestLine(autoByteBuffer, httpRequest)) {
            return;
        }
        if(!HttpSerializer.readHeaders(autoByteBuffer, httpRequest)){
            return;
        }

        if(!HttpSerializer.readContent(autoByteBuffer, httpRequest)){
            return;
        }
        super.decode(aioChannel, httpRequest, out);
    }
}
