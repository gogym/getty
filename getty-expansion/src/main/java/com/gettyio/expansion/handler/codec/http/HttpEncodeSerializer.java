/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.expansion.handler.codec.http;

import com.gettyio.core.buffer.AutoByteBuffer;

import java.io.IOException;
import java.util.Map;

/**
 * HttpEncodeSerializer.java
 *
 * @description:http编码序列化
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
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
