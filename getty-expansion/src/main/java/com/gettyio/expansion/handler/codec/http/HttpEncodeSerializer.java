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
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.expansion.handler.codec.http.request.HttpRequest;
import com.gettyio.expansion.handler.codec.http.response.HttpResponse;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP 编码序列化工具类。
 * <p>
 * 将 {@link HttpRequest} 或 {@link HttpResponse} 对象编码为 HTTP 协议字节流，
 * 包括初始行、头部和消息体的序列化。
 * </p>
 *
 * @author gogym
 */
public class HttpEncodeSerializer {


    /**
     * 编码 HTTP 请求行。
     * <p>
     * 格式: "METHOD URI VERSION\r\n"，例如 "GET /index.html HTTP/1.1\r\n"。
     * 使用 UTF-8 字符集编码。
     * </p>
     *
     * @param buffer  输出缓冲区
     * @param request 请求对象
     * @throws IOException 写入异常
     */
    public static void encodeInitialLine(AutoByteBuffer buffer, HttpRequest request) throws IOException {
        byte[] bytes = request.getHttpMethod().toString().getBytes(CharsetUtil.UTF_8);
        buffer.writeBytes(bytes);
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(request.getRequestUri().getBytes(CharsetUtil.UTF_8));
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(request.getHttpVersion().toString().getBytes(CharsetUtil.UTF_8));
        buffer.writeBytes(HttpConstants.CRLF);
    }


    /**
     * 编码 HTTP 响应状态行。
     * <p>
     * 格式: "VERSION CODE REASON\r\n"，例如 "HTTP/1.1 200 OK\r\n"。
     * 使用 UTF-8 字符集编码。
     * </p>
     *
     * @param buffer   输出缓冲区
     * @param response 响应对象
     * @throws IOException 写入异常
     */
    public static void encodeInitialLine(AutoByteBuffer buffer, HttpResponse response) throws IOException {
        byte[] bytes = response.getHttpVersion().toString().getBytes(CharsetUtil.UTF_8);
        buffer.writeBytes(bytes);
        buffer.writeByte(HttpConstants.SP);
        buffer.writeBytes(response.getHttpResponseStatus().getBytes());
        buffer.writeBytes(HttpConstants.CRLF);
    }


    /**
     * 编码所有 HTTP 头部字段。
     * <p>
     * 每个字段格式: "Name: Value\r\n"，所有字段后加一个空行 "\r\n" 表示头部结束。
     * 使用 {@link HttpMessage#getHeaderEntries()} 的 Iterable 视图遍历，
     * 避免创建临时 LinkedList。
     * </p>
     *
     * @param buffer  输出缓冲区
     * @param message 消息对象
     * @throws IOException 写入异常
     */
    public static void encodeHeaders(AutoByteBuffer buffer, HttpMessage message) throws IOException {
        for (Map.Entry<String, String> header : message.getHeaderEntries()) {
            byte[] key = header.getKey().getBytes(CharsetUtil.UTF_8);
            byte[] value = header.getValue().getBytes(CharsetUtil.UTF_8);
            buffer.writeBytes(key);
            buffer.writeBytes(HttpConstants.COLON_SP);
            buffer.writeBytes(value);
            buffer.writeBytes(HttpConstants.CRLF);
        }
        buffer.writeBytes(HttpConstants.CRLF);
    }

    /**
     * 编码请求消息体。
     * <p>
     * 如果消息体不为空，将原始字节写入缓冲区并追加 CRLF 结尾。
     * </p>
     *
     * @param buffer  输出缓冲区
     * @param request 请求对象
     * @throws IOException 写入异常
     */
    public static void encodeContent(AutoByteBuffer buffer, HttpRequest request) throws IOException {
        if (request.getHttpBody().getContent() != null) {
            buffer.writeBytes(request.getHttpBody().getContent());
        }
    }

    /**
     * 编码响应消息体。
     * <p>
     * 如果消息体不为空，将原始字节写入缓冲区并追加 CRLF 结尾。
     * </p>
     *
     * @param buffer   输出缓冲区
     * @param response 响应对象
     * @throws IOException 写入异常
     */
    public static void encodeContent(AutoByteBuffer buffer, HttpResponse response) throws IOException {
        if (response.getHttpBody().getContent() != null) {
            buffer.writeBytes(response.getHttpBody().getContent());
        }
    }
}
