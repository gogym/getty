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
import com.gettyio.core.util.StringUtil;
import com.gettyio.expansion.handler.codec.http.request.HttpRequest;
import com.gettyio.expansion.handler.codec.http.response.HttpResponse;
import com.gettyio.expansion.handler.codec.http.response.HttpResponseStatus;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpDecodeSerializer.java
 *
 * @description:http解码序列化
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class HttpDecodeSerializer {

    public static final int READ_LINE = 1;
    public static final int READ_HEADERS = 2;
    public static final int READ_CONTENT = 3;

    private static final String SP = " ";



    /**
     * 解析请求
     *
     * @param autoByteBuffer
     * @param httpMessage
     * @throws Exception
     */
    public static boolean read(AutoByteBuffer autoByteBuffer, HttpMessage httpMessage) throws Exception {
        if (httpMessage.getReadStatus() == HttpDecodeSerializer.READ_LINE) {
            if (!HttpDecodeSerializer.readLine(autoByteBuffer, httpMessage)) {
                return false;
            }
            httpMessage.setReadStatus(HttpDecodeSerializer.READ_HEADERS);
        }

        if (httpMessage.getReadStatus() == HttpDecodeSerializer.READ_HEADERS) {
            if (!HttpDecodeSerializer.readHeaders(autoByteBuffer, httpMessage)) {
                return false;
            }
            httpMessage.setReadStatus(HttpDecodeSerializer.READ_CONTENT);
        }

        if (httpMessage.getReadStatus() == HttpDecodeSerializer.READ_CONTENT) {
            if (!HttpDecodeSerializer.readContent(autoByteBuffer, httpMessage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取请求行
     *
     * @return java.lang.String
     * @params [autoByteBuffer]
     */
    private static boolean readLine(AutoByteBuffer autoByteBuffer, HttpMessage httpMessage) throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        while (autoByteBuffer.hasRemaining()) {
            byte nextByte = autoByteBuffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = autoByteBuffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    if (httpMessage instanceof HttpRequest) {
                        decodeQueryString(httpMessage.getSb().toString(), (HttpRequest) httpMessage);
                    } else if (httpMessage instanceof HttpResponse) {
                        decodeResponseLine(httpMessage.getSb().toString(), (HttpResponse) httpMessage);
                    }
                    httpMessage.getSb().setLength(0);
                    return true;
                }
            } else {
                httpMessage.getSb().append((char) nextByte);
            }
        }
        return false;
    }

    /**
     * 解析请求字符串
     *
     * @return void
     * @params [requestLine, request]
     */
    private static void decodeQueryString(String requestLine, HttpRequest request) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(SP);
        if (requestLineArray.length < 3) {
            request.setReadStatus(READ_LINE);
            return;
        }
        request.setHttpMethod(HttpMethod.valueOf(requestLineArray[0]));
        request.setRequestUri(requestLineArray[1]);
        request.setHttpVersion(HttpVersion.valueOf(requestLineArray[2]));

        String uri = requestLineArray[1];

        int at = uri.indexOf('?');
        String queryString = uri;
        if (at > 0) {
            queryString = uri.substring(0, at);
            String params = uri.substring(at);
            decodeParamsFromUri(params, request);
        }
        request.setQueryString(queryString);
    }

    /**
     * 解析请求参数
     *
     * @return void
     * @params [params, request]
     */
    private static void decodeParamsFromUri(String params, HttpRequest request) throws UnsupportedEncodingException {

        String charset = "UTF-8";

        int start = 0;
        int length = params.length();
        //跳过 '?'
        for (; start < length; start++) {
            if ('?' != params.charAt(start)) {
                break;
            }
        }
        int left = start;
        int middle = 0;
        for (; start < length; start++) {
            if ('=' == params.charAt(start)) {
                middle = start;
                for (; start < length; start++) {
                    char c = params.charAt(start);
                    if ('&' == c) {
                        String key = params.substring(left, middle);
                        String value = params.substring(middle + 1, start);
                        request.addParameter(URLDecoder.decode(key, charset), URLDecoder.decode(value, charset));
                        //跳过 '&'
                        for (; start < length; start++) {
                            if ('&' != params.charAt(start)) {
                                break;
                            }
                        }
                        left = start;
                        break;
                    }
                }
            }
        }
        if (middle > left) {
            String key = params.substring(left, middle);
            String value = params.substring(middle + 1);
            request.addParameter(URLDecoder.decode(key, charset), URLDecoder.decode(value, charset));
        }

    }

    private static void decodeResponseLine(String requestLine, HttpResponse response) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(SP);
        if (requestLineArray.length < 2) {
            response.setReadStatus(READ_LINE);
            return;
        }
        response.setHttpVersion(HttpVersion.valueOf(requestLineArray[0]));
        HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(Integer.parseInt(requestLineArray[1]));
        response.setHttpResponseStatus(responseStatus);
    }

    private static boolean readHeaders(AutoByteBuffer buffer, HttpMessage httpMessage) throws HttpException, AutoByteBuffer.ByteBufferException {

        while (buffer.hasRemaining()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    nextByte = buffer.readByte();
                    readHeader(httpMessage, httpMessage.getSb().toString());
                    //清空sb
                    httpMessage.getSb().setLength(0);

                    if (nextByte == HttpConstants.CR) {
                        nextByte = buffer.readByte();
                        if (nextByte == HttpConstants.LF) {
                            return true;
                        }
                    } else {
                        httpMessage.getSb().append((char) nextByte);
                    }
                }
            } else {
                httpMessage.getSb().append((char) nextByte);
            }
        }
        return false;
    }

    private static void readHeader(HttpMessage httpMessage, String header) {
        String[] kv = splitHeader(header);
        httpMessage.addHeader(kv[0], kv[1]);
    }

    private static String[] splitHeader(String sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = StringUtil.findNonWhitespace(sb, 0);
        for (nameEnd = nameStart; nameEnd < length; nameEnd++) {
            char ch = sb.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for (colonEnd = nameEnd; colonEnd < length; colonEnd++) {
            if (sb.charAt(colonEnd) == ':') {
                colonEnd++;
                break;
            }
        }

        valueStart = StringUtil.findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            return new String[]{
                    sb.substring(nameStart, nameEnd),
                    ""
            };
        }

        valueEnd = StringUtil.findEndOfString(sb);
        return new String[]{
                sb.substring(nameStart, nameEnd),
                sb.substring(valueStart, valueEnd)
        };
    }

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

        byte[] bytes = new byte[Long.valueOf(contentLength).intValue()];
        buffer.readBytes(bytes);
        httpMessage.getHttpBody().setContent(bytes);

        if (httpMessage instanceof HttpRequest) {
            if (httpMessage.getHttpBody().getContentType().contains("multipart/")) {
                //需要解multipart/form-data; boundary=--------------------------806979702282165592642856
                readMultipart((HttpRequest) httpMessage);
            } else {
                decodeParamsFromUri(new String(httpMessage.getHttpBody().getContent()), (HttpRequest) httpMessage);
            }
        }
        return true;
    }

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
                        case BOUNDARY:
                            String line = new String(autoByteBuffer.readableBytesArray());
                            if (line.equals(boundary)) {
                                step = Step.HEADER;
                                fileItem = new FieldItem();
                                autoByteBuffer.clear();
                            }
                            continue;
                        case HEADER:
                            Map map = getKeyValueAttribute(new String(autoByteBuffer.readableBytesArray()));
                            headers.putAll(map);

                            fileItem.setContentDisposition(headers.get("Content-Disposition".toLowerCase()));
                            fileItem.setContentType(headers.get("Content-Type".toLowerCase()));
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
                        case BODY:

                            String body = new String(autoByteBuffer.readableBytesArray());
                            if (body.equals(endBoundary)) {
                                step = Step.END;
                                if (fileItem.isFormField()) {
                                    String v = new String(bodyBuffer.readableBytesArray());
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
                                    String v = new String(bodyBuffer.readableBytesArray());
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
                                //读取到另一个数组暂存
                                bodyBuffer.writeBytes(autoByteBuffer);
                                autoByteBuffer.clear();
                                continue;
                            }
//                            nextByte = content[index];
//                            if (nextByte == HttpConstants.CR) {
//                                index++;
//                                nextByte = content[index];
//                                if (nextByte == HttpConstants.LF) {
//                                    index++;
//                                    step = Step.END;
//                                    autoByteBuffer.clear();
//                                    continue;
//                                }
//                            }
//                            break;

//                        case END:
//                            String end = new String(autoByteBuffer.readableBytesArray());
//                            if (end.equals(endBoundary)) {
//                                return true;
//                            } else if (end.equals(boundary)) {
//                                step = Step.HEADER;
//                                fileItem = new FileItem();
//                                autoByteBuffer.clear();
//                                continue;
//                            }
                    }
                }
            }
            autoByteBuffer.writeByte(nextByte);
        }
        return false;
    }


    private static String getSubAttribute(String str, String name) {
        int index = str.indexOf(name + "=");
        if (index == -1) {
            index = str.indexOf(name + ":");
            if (index == -1) {
                return null;
            }
        }
        int startIndex = index + 1 + name.length();
        char[] c = new char[str.length() - startIndex];
        for (int i = 0; i < c.length; i++) {
            char charAt = str.charAt(i + startIndex);
            if (charAt == ';') {
                break;
            }
            c[i] = charAt;
        }
        return new String(c).replaceAll("['\"]", "");
    }

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

    private enum Step {
        BOUNDARY, HEADER, BODY, END
    }
}
