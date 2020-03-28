package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpRequestDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonBlockQueue;

public class HttpRequestDecoder extends ObjectToMessageDecoder {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(HttpRequestDecoder.class);


    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    HttpRequest httpRequest;

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        autoByteBuffer.writeBytes((byte[]) obj);

        if (httpRequest == null) {
            httpRequest = new HttpRequest();
            httpRequest.setReadStatus(HttpDecodeSerializer.readLine);
        }

        if (httpRequest.getReadStatus() == HttpDecodeSerializer.readLine) {
            if (!HttpDecodeSerializer.readRequestLine(autoByteBuffer, httpRequest)) {
                return;
            }
            httpRequest.setReadStatus(HttpDecodeSerializer.readHeaders);
        }

        if (httpRequest.getReadStatus() == HttpDecodeSerializer.readHeaders) {
            if (!HttpDecodeSerializer.readHeaders(autoByteBuffer, httpRequest)) {
                return;
            }
            httpRequest.setReadStatus(HttpDecodeSerializer.readContent);
        }


        if (httpRequest.getReadStatus() == HttpDecodeSerializer.readContent) {
            if (!HttpDecodeSerializer.readContent(autoByteBuffer, httpRequest)) {
                return;
            }
        }
        super.decode(socketChannel, httpRequest, out);
        autoByteBuffer.clear();
        httpRequest = null;
    }
}
