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
    HttpRequest httpRequest;

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        autoByteBuffer.writeBytes((byte[]) obj);

        if (httpRequest == null) {
            httpRequest = new HttpRequest();
            httpRequest.setReadStatus(HttpSerializer.readRequestLine);
        }

        if (httpRequest.getReadStatus() == HttpSerializer.readRequestLine) {
            if (!HttpSerializer.readRequestLine(autoByteBuffer, httpRequest)) {
                return;
            }
            httpRequest.setReadStatus(HttpSerializer.readHeaders);
        }

        if (httpRequest.getReadStatus() == HttpSerializer.readHeaders) {
            if (!HttpSerializer.readHeaders(autoByteBuffer, httpRequest)) {
                return;
            }
            httpRequest.setReadStatus(HttpSerializer.readContent);
        }


        if (httpRequest.getReadStatus() == HttpSerializer.readContent) {
            if (!HttpSerializer.readContent(autoByteBuffer, httpRequest)) {
                return;
            }
        }
        super.decode(aioChannel, httpRequest, out);
        autoByteBuffer.clear();
        httpRequest = null;
    }
}
