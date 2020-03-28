package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpResponseDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.util.LinkedNonBlockQueue;

public class HttpResponseDecoder extends ObjectToMessageDecoder {

    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    HttpResponse httpResponse;

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        autoByteBuffer.writeBytes((byte[]) obj);

        if (httpResponse == null) {
            httpResponse = new HttpResponse();
            httpResponse.setReadStatus(HttpDecodeSerializer.readLine);
        }

        if (httpResponse.getReadStatus() == HttpDecodeSerializer.readLine) {
            if (!HttpDecodeSerializer.readResponseLine(autoByteBuffer, httpResponse)) {
                return;
            }
            httpResponse.setReadStatus(HttpDecodeSerializer.readHeaders);
        }

        if (httpResponse.getReadStatus() == HttpDecodeSerializer.readHeaders) {
            if (!HttpDecodeSerializer.readHeaders(autoByteBuffer, httpResponse)) {
                return;
            }
            httpResponse.setReadStatus(HttpDecodeSerializer.readContent);
        }


        if (httpResponse.getReadStatus() == HttpDecodeSerializer.readContent) {
            if (!HttpDecodeSerializer.readContent(autoByteBuffer, httpResponse)) {
                return;
            }
        }
        super.decode(socketChannel, httpResponse, out);
        autoByteBuffer.clear();
        httpResponse = null;
    }
}
