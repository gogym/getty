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
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

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

    protected static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(HttpDecodeSerializer.class);
    public static final int READLINE = 1;
    public static final int READHEADERS = 2;
    public static final int READCONTENT = 3;

    /**
     * 读取请求行
     *
     * @return java.lang.String
     * @params [autoByteBuffer]
     */
    public static boolean readRequestLine(AutoByteBuffer autoByteBuffer, HttpRequest request) throws HttpException, AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(64);
        int lineLength = 0;
        int limit = autoByteBuffer.writerIndex();
        int position = autoByteBuffer.readerIndex();
        for (int index = position; index < limit; index++) {
            byte nextByte = autoByteBuffer.read(index);
            if (nextByte == HttpConstants.CR) {
                nextByte = autoByteBuffer.read(index + 1);
                if (nextByte == HttpConstants.LF) {
                    autoByteBuffer.readerIndex(index + 2);
                    decodeQueryString(sb.toString(), request);
                    return true;
                }
            } else if (nextByte == HttpConstants.LF) {
                autoByteBuffer.readerIndex(index + 2);
                decodeQueryString(sb.toString(), request);
                return true;
            } else {
                if (lineLength >= autoByteBuffer.writerIndex()) {
                    throw new HttpException(HttpResponseStatus.REQUEST_URI_TOO_LONG, "An HTTP line is larger than " + autoByteBuffer.writerIndex() + " bytes.");
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
        return false;
    }


    public static boolean readResponseLine(AutoByteBuffer autoByteBuffer, HttpResponse response) throws HttpException, AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(64);
        int lineLength = 0;
        int limit = autoByteBuffer.writerIndex();
        int position = autoByteBuffer.readerIndex();
        for (int index = position; index < limit; index++) {
            byte nextByte = autoByteBuffer.read(index);
            if (nextByte == HttpConstants.CR) {
                nextByte = autoByteBuffer.read(index + 1);
                if (nextByte == HttpConstants.LF) {
                    autoByteBuffer.readerIndex(index + 2);
                    decodeResponseLine(sb.toString(), response);
                    return true;
                }
            } else if (nextByte == HttpConstants.LF) {
                autoByteBuffer.readerIndex(index + 2);
                decodeResponseLine(sb.toString(), response);
                return true;
            } else {
                if (lineLength >= autoByteBuffer.writerIndex()) {
                    throw new HttpException(HttpResponseStatus.REQUEST_URI_TOO_LONG, "An HTTP line is larger than " + autoByteBuffer.writerIndex() + " bytes.");
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
        return false;
    }


    private static void decodeResponseLine(String requestLine, HttpResponse response) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(" ");
        if (requestLineArray.length < 2) {
            throw new UnsupportedEncodingException("Wrong Request-Line format: " + requestLine);
        }
        response.setHttpVersion(HttpVersion.valueOf(requestLineArray[0]));
        HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(Integer.valueOf(requestLineArray[1]));
        response.setHttpResponseStatus(responseStatus);
    }

    /**
     * 解析请求字符串
     *
     * @return void
     * @params [requestLine, request]
     */
    private static void decodeQueryString(String requestLine, HttpRequest request) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(" ");
        if (requestLineArray.length < 2) {
            throw new UnsupportedEncodingException("Wrong Request-Line format: " + requestLine);
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
    public static void decodeParamsFromUri(String params, HttpRequest request) throws UnsupportedEncodingException {

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


    /**
     * 读取消息头
     *
     * @return boolean
     * @params [buffer, request]
     */
    public static boolean readHeaders(AutoByteBuffer buffer, HttpRequest request) throws HttpException, AutoByteBuffer.ByteBufferException {
        StringBuilder sb = new StringBuilder(64);
        int limit = buffer.writerIndex();
        int position = buffer.readerIndex();
        int lineLength = 0;
        for (int index = position; index < limit; index++) {
            byte nextByte = buffer.read(index);
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.read(index + 1);
                if (nextByte == HttpConstants.LF) {
                    buffer.readerIndex(index);
                    if (lineLength == 0) {
                        buffer.readerIndex(index + 2);
                        return true;
                    } else {
                        buffer.readerIndex(index);
                    }
                    readHeader(request, sb.toString());
                    lineLength = 0;
                    sb.setLength(0);
                    index++;
                }
            } else if (nextByte == HttpConstants.LF) {
                if (lineLength == 0) {
                    buffer.readerIndex(index + 2);
                    return true;
                } else {
                    buffer.readerIndex(index);
                }
                readHeader(request, sb.toString());
                lineLength = 0;
                sb.setLength(0);
                index++;
            } else {
                if (lineLength >= buffer.writerIndex()) {
                    throw new HttpException(HttpResponseStatus.BAD_REQUEST, "An HTTP header is larger than " + buffer.writerIndex() + " bytes.");
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
        return false;
    }

    public static boolean readHeaders(AutoByteBuffer buffer, HttpResponse response) throws HttpException, AutoByteBuffer.ByteBufferException {
        StringBuilder sb = new StringBuilder(64);
        int limit = buffer.writerIndex();
        int position = buffer.readerIndex();
        int lineLength = 0;
        for (int index = position; index < limit; index++) {
            byte nextByte = buffer.read(index);
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.read(index + 1);
                if (nextByte == HttpConstants.LF) {
                    buffer.readerIndex(index);
                    if (lineLength == 0) {
                        buffer.readerIndex(index + 2);
                        return true;
                    } else {
                        buffer.readerIndex(index);
                    }
                    readHeader(response, sb.toString());
                    lineLength = 0;
                    sb.setLength(0);
                    index++;
                }
            } else if (nextByte == HttpConstants.LF) {
                if (lineLength == 0) {
                    buffer.readerIndex(index + 2);
                    return true;
                } else {
                    buffer.readerIndex(index);
                }
                readHeader(response, sb.toString());
                lineLength = 0;
                sb.setLength(0);
                index++;
            } else {
                if (lineLength >= buffer.writerIndex()) {
                    throw new HttpException(HttpResponseStatus.BAD_REQUEST, "An HTTP header is larger than " + buffer.writerIndex() + " bytes.");
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
        return false;
    }


    private static void readHeader(HttpRequest request, String header) {
        String[] kv = splitHeader(header);
        request.addHeader(kv[0], kv[1]);
    }

    private static void readHeader(HttpResponse response, String header) {
        String[] kv = splitHeader(header);
        response.addHeader(kv[0], kv[1]);
    }

    private static String[] splitHeader(String sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
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

        valueStart = findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            return new String[]{
                    sb.substring(nameStart, nameEnd),
                    ""
            };
        }

        valueEnd = findEndOfString(sb);
        return new String[]{
                sb.substring(nameStart, nameEnd),
                sb.substring(valueStart, valueEnd)
        };
    }

    /**
     * 读取消息体
     *
     * @return boolean
     * @params [buffer, request]
     */
    public static boolean readContent(AutoByteBuffer buffer, HttpRequest request) throws Exception {
        long contentLength = HttpHeaders.getContentLength(request);
        if (contentLength <= 0) {
            return true;
        }
        int remain = buffer.readableBytes();
        if (remain < contentLength) {
            return false;
        }
        request.getHttpBody().setContentLength(contentLength);
        String contentType = request.getHeader(HttpHeaders.Names.CONTENT_TYPE);
        request.getHttpBody().setContentType(contentType);

        byte[] bytes = new byte[Long.valueOf(contentLength).intValue()];
        buffer.readBytes(bytes);
        request.getHttpBody().setContent(bytes);

        if (request.getHttpBody().getContentType().contains("multipart/")) {
            //需要解multipart/form-data; boundary=--------------------------806979702282165592642856
            //System.out.printf(new String(request.getHttpBody().getContent()));
            readMultipart(request);
        } else {
            decodeParamsFromUri(new String(request.getHttpBody().getContent()), request);
        }

        return true;
    }


    /**
     * 读取消息体
     *
     * @return boolean
     * @params [buffer, request]
     */
    public static boolean readContent(AutoByteBuffer buffer, HttpResponse response) throws Exception {
        long contentLength = HttpHeaders.getContentLength(response);
        if (contentLength <= 0) {
            return true;
        }
        int remain = buffer.readableBytes();
        if (remain < contentLength) {
            return false;
        }
        response.getHttpBody().setContentLength(contentLength);
        String contentType = response.getHeader(HttpHeaders.Names.CONTENT_TYPE);
        response.getHttpBody().setContentType(contentType);

        byte[] bytes = new byte[Long.valueOf(contentLength).intValue()];
        buffer.readBytes(bytes);
        response.getHttpBody().setContent(bytes);

        return true;
    }


    /**
     * 读取Multipart
     *
     * @return boolean
     * @params [request]
     */
    public static boolean readMultipart(HttpRequest request) {

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


    private static int findNonWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result++) {
            if (!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private static int findWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result++) {
            if (Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private static int findEndOfString(String sb) {
        int result;
        for (result = sb.length(); result > 0; result--) {
            if (!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }

    public static String getSubAttribute(String str, String name) {
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

    public static Map<String, String> getKeyValueAttribute(String str) {
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

    public static enum Step {
        BOUNDARY, HEADER, BODY, END
    }
}
