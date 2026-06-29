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
import com.gettyio.expansion.handler.codec.http.response.HttpResponseStatus;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 解码序列化工具类。
 * <p>
 * 提供 HTTP 请求/响应的行、头部、内容解析，
 * 支持 URL 参数解析和 multipart/form-data 文件上传解析。
 * </p>
 *
 * @author gogym
 */
public class HttpDecodeSerializer {

    /** 解析状态：读取请求行/状态行 */
    public static final int READ_LINE = 1;
    /** 解析状态：读取头部 */
    public static final int READ_HEADERS = 2;
    /** 解析状态：读取消息体 */
    public static final int READ_CONTENT = 3;

    /**
     * 解码过程中的临时状态，由 Decoder 持有，不属于 HttpMessage。
     */
    public static class ParseState {
        /** 当前解析阶段 */
        int readStatus = READ_LINE;
        /** 行/头部解析用临时缓冲区 */
        final StringBuilder sb = new StringBuilder();

        /**
         * 重置解析状态，开始解析新消息。
         */
        public void reset() {
            readStatus = READ_LINE;
            sb.setLength(0);
        }
    }


    /**
     * 解析请求/响应
     *
     * @param autoByteBuffer 累积的接收缓冲区
     * @param httpMessage    消息对象
     * @param state          解析状态（由 Decoder 持有）
     * @return true 表示解析完成，false 表示数据不完整
     */
    public static boolean read(AutoByteBuffer autoByteBuffer, HttpMessage httpMessage, ParseState state) throws Exception {
        if (state.readStatus == READ_LINE) {
            if (!readLine(autoByteBuffer, httpMessage, state.sb, state)) {
                return false;
            }
            // readLine 返回 true 表示请求行/状态行已读取完毕，进入头部解析阶段
            state.readStatus = READ_HEADERS;
        }

        if (state.readStatus == READ_HEADERS) {
            if (!readHeaders(autoByteBuffer, httpMessage, state.sb)) {
                return false;
            }
            state.readStatus = READ_CONTENT;
        }

        if (state.readStatus == READ_CONTENT) {
            if (!readContent(autoByteBuffer, httpMessage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取请求行/状态行，直到遇到第一个 CRLF。
     * <p>
     * 逐字节读取并累积到 StringBuilder，遇到 \r\n 时调用对应的解析方法。
     * 如果解析失败（格式不完整），将 state.readStatus 重置为 READ_LINE。
     * </p>
     *
     * @param autoByteBuffer 累积的接收缓冲区
     * @param httpMessage    消息对象
     * @param sb             行解析用临时缓冲区
     * @param state          解析状态
     * @return true 表示行读取完成，false 表示数据不完整
     * @throws AutoByteBuffer.ByteBufferException 缓冲区读取异常
     * @throws UnsupportedEncodingException       字符编码异常
     */
    private static boolean readLine(AutoByteBuffer autoByteBuffer, HttpMessage httpMessage, StringBuilder sb, ParseState state) throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        while (autoByteBuffer.hasRemaining()) {
            byte nextByte = autoByteBuffer.readByte();
            if (nextByte == HttpConstants.CR) {
                if (!autoByteBuffer.hasRemaining()) {
                    return false;
                }
                nextByte = autoByteBuffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    if (httpMessage instanceof HttpRequest) {
                        if (!decodeQueryString(sb.toString(), (HttpRequest) httpMessage)) {
                            state.readStatus = READ_LINE;
                        }
                    } else if (httpMessage instanceof HttpResponse) {
                        if (!decodeResponseLine(sb.toString(), (HttpResponse) httpMessage)) {
                            state.readStatus = READ_LINE;
                        }
                    }
                    sb.setLength(0);
                    return true;
                }
            } else {
                sb.append((char) nextByte);
            }
        }
        return false;
    }

    /**
     * 解析请求行（去 split，零分配）。
     * 格式: "METHOD URI VERSION"
     */
    private static boolean decodeQueryString(String requestLine, HttpRequest request) throws UnsupportedEncodingException {
        // 找第一个空格
        int sp1 = requestLine.indexOf(' ');
        if (sp1 < 0) {
            return false;
        }
        // 找第二个空格
        int sp2 = requestLine.indexOf(' ', sp1 + 1);
        if (sp2 < 0) {
            return false;
        }

        String method = requestLine.substring(0, sp1);
        String uri = requestLine.substring(sp1 + 1, sp2);
        String version = requestLine.substring(sp2 + 1);

        request.setHttpMethod(HttpMethod.valueOf(method));
        request.setRequestUri(uri);
        request.setHttpVersion(HttpVersion.valueOf(version));

        // 解析 URI 中的查询参数
        int qmark = uri.indexOf('?');
        if (qmark > 0) {
            request.setQueryString(uri.substring(0, qmark));
            decodeFormBody(uri.substring(qmark + 1), request);
        } else {
            request.setQueryString(uri);
        }
        return true;
    }

    /**
     * 解析 URL 编码的参数（key1=val1&key2=val2），不带 '?' 前缀。
     */
    private static void decodeFormBody(String params, HttpRequest request) throws UnsupportedEncodingException {
        final String charset = "UTF-8";
        int len = params.length();
        int start = 0;

        while (start < len) {
            int eqIdx = params.indexOf('=', start);
            if (eqIdx < 0) {
                break;
            }
            int ampIdx = params.indexOf('&', eqIdx + 1);
            String key = params.substring(start, eqIdx);
            String value;
            if (ampIdx < 0) {
                value = params.substring(eqIdx + 1);
                start = len;
            } else {
                value = params.substring(eqIdx + 1, ampIdx);
                start = ampIdx + 1;
            }
            request.addParameter(URLDecoder.decode(key, charset), URLDecoder.decode(value, charset));
        }
    }

    /**
     * 解析响应状态行（去 split，零分配）。
     * <p>
     * 格式: "HTTP/1.1 200 OK"，通过 indexOf 定位空格分隔符，
     * 避免 String.split() 的数组分配开销。
     * </p>
     *
     * @param statusLine 状态行字符串
     * @param response   响应消息对象
     * @return true 表示解析成功，false 表示格式不完整
     * @throws UnsupportedEncodingException 字符编码异常
     */
    private static boolean decodeResponseLine(String statusLine, HttpResponse response) throws UnsupportedEncodingException {
        // 格式: "HTTP/1.1 200 OK"
        int sp1 = statusLine.indexOf(' ');
        if (sp1 < 0) {
            return false;
        }
        int sp2 = statusLine.indexOf(' ', sp1 + 1);
        String versionStr = statusLine.substring(0, sp1);
        String codeStr;
        if (sp2 < 0) {
            // 没有 reason phrase
            codeStr = statusLine.substring(sp1 + 1);
        } else {
            codeStr = statusLine.substring(sp1 + 1, sp2);
        }
        response.setHttpVersion(HttpVersion.valueOf(versionStr));
        HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(Integer.parseInt(codeStr));
        response.setHttpResponseStatus(responseStatus);
        return true;
    }

    /**
     * 读取所有头部字段，遇到空行（CRLFCRLF）时返回 true。
     * <p>
     * 每个头部行解析后直接通过 {@link #addHeaderFromLine} 添加到消息对象，
     * 避免创建中间 String[] 数组。
     * </p>
     *
     * @param buffer      累积的接收缓冲区
     * @param httpMessage 消息对象
     * @param sb          行解析用临时缓冲区
     * @return true 表示头部读取完成，false 表示数据不完整
     * @throws HttpException                      HTTP 协议异常
     * @throws AutoByteBuffer.ByteBufferException 缓冲区读取异常
     */
    private static boolean readHeaders(AutoByteBuffer buffer, HttpMessage httpMessage, StringBuilder sb) throws HttpException, AutoByteBuffer.ByteBufferException {

        while (buffer.hasRemaining()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                if (!buffer.hasRemaining()) {
                    return false;
                }
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    // 头部行的 CRLF 结束，添加该头部
                    addHeaderFromLine(httpMessage, sb.toString());
                    sb.setLength(0);

                    // 检查下一个字节：CR 表示空行（头部结束），否则是下一个头部的开始
                    if (!buffer.hasRemaining()) {
                        return false;
                    }
                    nextByte = buffer.readByte();
                    if (nextByte == HttpConstants.CR) {
                        // 空行的 CR，确认后面有 LF
                        if (!buffer.hasRemaining()) {
                            return false;
                        }
                        nextByte = buffer.readByte();
                        if (nextByte == HttpConstants.LF) {
                            return true;
                        }
                    } else {
                        // 下一个头部的首字节，放回 sb
                        sb.append((char) nextByte);
                    }
                } else {
                    // CR 后面不是 LF，将两个字节都追加到 sb
                    sb.append((char) HttpConstants.CR);
                    sb.append((char) nextByte);
                }
            } else {
                sb.append((char) nextByte);
            }
        }
        return false;
    }

    /**
     * 解析单行 header 并直接添加到 message（避免创建 String[]）。
     */
    private static void addHeaderFromLine(HttpMessage httpMessage, String header) {
        final int length = header.length();
        int nameStart = findNonWhitespace(header, 0);
        int nameEnd;
        for (nameEnd = nameStart; nameEnd < length; nameEnd++) {
            char ch = header.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        int colonEnd;
        for (colonEnd = nameEnd; colonEnd < length; colonEnd++) {
            if (header.charAt(colonEnd) == ':') {
                colonEnd++;
                break;
            }
        }

        int valueStart = findNonWhitespace(header, colonEnd);
        String name = header.substring(nameStart, nameEnd);
        if (valueStart == length) {
            httpMessage.addHeader(name, "");
        } else {
            int valueEnd = findEndOfString(header);
            httpMessage.addHeader(name, header.substring(valueStart, valueEnd));
        }
    }

    /**
     * 从指定位置开始向后查找第一个非空白字符的位置。
     *
     * @param s     目标字符串
     * @param start 起始位置
     * @return 第一个非空白字符的索引
     */
    private static int findNonWhitespace(String s, int start) {
        int i = start;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
        }
        return i;
    }

    /**
     * 从字符串末尾向前查找最后一个非空白字符的位置。
     *
     * @param s 目标字符串
     * @return 最后一个非空白字符之后的位置索引
     */
    private static int findEndOfString(String s) {
        int i = s.length();
        for (; i > 0; i--) {
            char c = s.charAt(i - 1);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
        }
        return i;
    }

    /**
     * 读取 HTTP 消息体。
     * <p>
     * 根据 Content-Length 头部确定消息体长度，数据不足时返回 false 等待更多数据。
     * 对于请求消息，根据 Content-Type 自动选择解析方式：
     * <ul>
     *   <li>multipart/form-data：调用 {@link #readMultipart} 解析文件上传</li>
     *   <li>application/x-www-form-urlencoded：调用 {@link #decodeFormBody} 解析表单参数</li>
     * </ul>
     * </p>
     *
     * @param buffer      累积的接收缓冲区
     * @param httpMessage 消息对象
     * @return true 表示消息体读取完成，false 表示数据不完整
     * @throws Exception 解析异常
     */
    private static boolean readContent(AutoByteBuffer buffer, HttpMessage httpMessage) throws Exception {
        long contentLength = HttpHeaders.getContentLength(httpMessage);
        if (contentLength <= 0) {
            return true;
        }
        int remain = buffer.readableBytes();
        if (remain < contentLength) {
            return false;
        }
        httpMessage.getHttpBody().setContentLength(contentLength);
        String contentType = httpMessage.getHeader(HttpHeaders.Names.CONTENT_TYPE);
        httpMessage.getHttpBody().setContentType(contentType);

        byte[] bytes = new byte[(int) contentLength];
        buffer.readBytes(bytes);
        httpMessage.getHttpBody().setContent(bytes);

        if (httpMessage instanceof HttpRequest) {
            if (contentType != null && contentType.contains("multipart/")) {
                // 解析 multipart/form-data
                readMultipart((HttpRequest) httpMessage);
            } else {
                // 解析 application/x-www-form-urlencoded 消息体
                decodeFormBody(new String(bytes, CharsetUtil.UTF_8), (HttpRequest) httpMessage);
            }
        }
        return true;
    }

    /**
     * 解析 multipart/form-data 格式的消息体。
     * <p>
     * 按 boundary 分隔符拆分为多个部分，每部分包含头部和消息体。
     * 支持普通表单字段和文件上传字段，解析结果通过
     * {@link HttpRequest#addParameter} 和 {@link HttpRequest#addFieldItem} 存储。
     * </p>
     *
     * @param request 请求对象，解析结果直接写入
     * @return true 表示解析完成
     * @throws NullPointerException 当 boundary 未找到时抛出
     */
    private static boolean readMultipart(HttpRequest request) {

        int indexOfBoundary = request.getHttpBody().getContentType().indexOf("boundary=");
        if (indexOfBoundary == -1) {
            throw new NullPointerException("boundary is null");
        }
        String boundary = request.getHttpBody().getContentType().substring(indexOfBoundary);
        boundary = "--" + getSubAttribute(boundary, "boundary");
        String endBoundary = boundary + "--";

        byte[] content = request.getHttpBody().getContent();
        long contentLength = request.getHttpBody().getContentLength();
        Step step = Step.BOUNDARY;

        AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
        AutoByteBuffer bodyBuffer = AutoByteBuffer.newByteBuffer();
        FieldItem fileItem = null;
        Map<String, String> headers = new HashMap<>();

        for (int index = 0; index < contentLength; index++) {
            byte nextByte = content[index];
            if (nextByte == HttpConstants.CR) {
                index++;
                nextByte = content[index];
                if (nextByte == HttpConstants.LF) {
                    //index++;
                    switch (step) {
                        case BOUNDARY: {
                            String line = new String(autoByteBuffer.readableBytesArray(), CharsetUtil.UTF_8);
                            if (line.equals(boundary)) {
                                step = Step.HEADER;
                                fileItem = new FieldItem();
                                autoByteBuffer.clear();
                            }
                            continue;
                        }
                        case HEADER: {
                            String headerLine = new String(autoByteBuffer.readableBytesArray(), CharsetUtil.UTF_8);
                            Map<String, String> map = getKeyValueAttribute(headerLine);
                            if (map != null) {
                                headers.putAll(map);
                            }

                            fileItem.setContentDisposition(headers.get("content-disposition"));
                            fileItem.setContentType(headers.get("content-type"));
                            String name = headers.get("name");
                            fileItem.setName(name);
                            name = headers.get("filename");
                            if (name != null) {
                                fileItem.setFilename(name);
                                fileItem.setFormField(false);
                            }

                            nextByte = content[index + 1];
                            if (nextByte == HttpConstants.CR) {
                                index++;
                                nextByte = content[index + 1];
                                if (nextByte == HttpConstants.LF) {
                                    index++;
                                    step = Step.BODY;
                                }
                            }
                            autoByteBuffer.clear();
                            continue;
                        }
                        case BODY: {
                            String body = new String(autoByteBuffer.readableBytesArray(), CharsetUtil.UTF_8);
                            if (body.equals(endBoundary)) {
                                step = Step.END;
                                if (fileItem.isFormField()) {
                                    String v = new String(bodyBuffer.readableBytesArray(), CharsetUtil.UTF_8);
                                    request.addParameter(fileItem.getName(), v);
                                    fileItem.setValue(v);
                                } else {
                                    fileItem.setFile(bodyBuffer.readableBytesArray());
                                }
                                request.addFieldItem(fileItem.getName(), fileItem);
                                return true;
                            } else if (body.equals(boundary)) {
                                step = Step.HEADER;
                                if (fileItem.isFormField()) {
                                    String v = new String(bodyBuffer.readableBytesArray(), CharsetUtil.UTF_8);
                                    request.addParameter(fileItem.getName(), v);
                                    fileItem.setValue(v);
                                } else {
                                    fileItem.setFile(bodyBuffer.readableBytesArray());
                                }
                                request.addFieldItem(fileItem.getName(), fileItem);

                                fileItem = new FieldItem();
                                headers.clear();
                                autoByteBuffer.clear();
                                bodyBuffer.clear();
                                continue;
                            } else {
                                // 非边界行，累积到消息体缓冲区
                                bodyBuffer.writeBytes(autoByteBuffer);
                                autoByteBuffer.clear();
                            }
                        }
                    }
                }
            }
            autoByteBuffer.writeByte(nextByte);
        }
        return false;
    }


    /**
     * 从属性字符串中提取指定名称的值。
     * <p>
     * 支持 "name=value" 和 "name:value" 两种格式，值以分号或字符串结尾为界。
     * 自动去除单引号和双引号。
     * </p>
     *
     * @param str  属性字符串，例如 "boundary=----12345"
     * @param name 属性名称
     * @return 属性值（去除引号后），未找到时返回 null
     */
    private static String getSubAttribute(String str, String name) {
        int index = str.indexOf(name + "=");
        if (index == -1) {
            index = str.indexOf(name + ":");
            if (index == -1) {
                return null;
            }
        }
        int startIndex = index + 1 + name.length();
        int endIndex = str.indexOf(';', startIndex);
        if (endIndex < 0) {
            endIndex = str.length();
        }
        return str.substring(startIndex, endIndex).replaceAll("['\"]", "");
    }

    /**
     * 解析键值对属性字符串。
     * <p>
     * 将分号分隔的属性字符串拆分为多个键值对，支持冒号和等号两种分隔符。
     * 键名统一转为小写，自动去除单引号和双引号。
     * </p>
     *
     * @param str 属性字符串，例如 "Content-Disposition: form-data; name=\"field\""
     * @return 键值对映射，不包含任何有效键值对时返回 null
     */
    private static Map<String, String> getKeyValueAttribute(String str) {
        int index = str.indexOf(":");
        int index2 = str.indexOf("=");
        if (index == -1 && index2 == -1) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        String[] strings = str.split(";");

        for (String s : strings) {
            if (s.contains(":")) {
                String[] arr = s.split(":");
                map.put(arr[0].toLowerCase().trim().replaceAll("['\"]", ""), arr[1].trim().replaceAll("['\"]", ""));
            } else if (s.contains("=")) {
                String[] arr = s.split("=");
                map.put(arr[0].toLowerCase().trim().replaceAll("['\"]", ""), arr[1].trim().replaceAll("['\"]", ""));
            }

        }
        return map;
    }

    /**
     * multipart 解析状态机阶段。
     */
    private enum Step {
        /** 读取 boundary 分隔线 */
        BOUNDARY,
        /** 读取字段头部 */
        HEADER,
        /** 读取字段消息体 */
        BODY,
        /** 解析结束 */
        END
    }
}
