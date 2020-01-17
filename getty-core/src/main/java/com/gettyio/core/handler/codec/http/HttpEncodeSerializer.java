package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpEncodeSerializer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/16
 */

import com.gettyio.core.buffer.AutoByteBuffer;

import java.io.IOException;
import java.util.Map;

public class HttpEncodeSerializer {


    public static void encodeInitialLine(AutoByteBuffer buffer, HttpRequest request) throws IOException {
        byte[] bytes = request.getHttpMethod().toString().getBytes();
        buffer.writeBytes(bytes);
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(request.getRequestUri().getBytes());
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(request.getHttpVersion().toString().getBytes());
        buffer.writeBytes(HttpConstants.CRLF);
    }


    public static void encodeInitialLine(AutoByteBuffer buffer, HttpResponse response) throws IOException {
        byte[] bytes = response.getHttpVersion().toString().getBytes();
        buffer.writeBytes(bytes);
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(response.getHttpResponseStatus().getBytes());
        buffer.writeBytes(HttpConstants.CRLF);
    }


    public static void encodeHeaders(AutoByteBuffer buffer, HttpMessage message) throws IOException {
        for (Map.Entry<String, String> header : message.getHeaders()) {
            byte[] key = header.getKey().getBytes();
            byte[] value = header.getValue().getBytes();
            buffer.writeBytes(key);
            buffer.writeBytes(HttpConstants.COLON_SP);
            buffer.writeBytes(value);
            buffer.writeBytes(HttpConstants.CRLF);
        }
        buffer.writeBytes(HttpConstants.CRLF);
    }

    public static void encodeContent(AutoByteBuffer buffer, HttpRequest request) throws IOException {

        if (request.getHttpBody().getContent() != null) {
            buffer.writeBytes(request.getHttpBody().getContent());
            buffer.writeBytes(HttpConstants.CRLF);
        }

    }

    public static void encodeContent(AutoByteBuffer buffer, HttpResponse response) throws IOException {
        if (response.getHttpBody().getContent() != null) {
            buffer.writeBytes(response.getHttpBody().getContent());
            buffer.writeBytes(HttpConstants.CRLF);
        }
    }
}
