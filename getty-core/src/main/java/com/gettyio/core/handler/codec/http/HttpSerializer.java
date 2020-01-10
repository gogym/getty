package com.gettyio.core.handler.codec.http;/*
 * 类名：WebSocketHandShak
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/2
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class HttpSerializer {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(AioChannel.class);


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


    private static void decodeQueryString(String requestLine, HttpRequest request) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(" ");
        if (requestLineArray.length < 2) {
            throw new UnsupportedEncodingException("Wrong Request-Line format: " + requestLine);
        }
        request.setHttpMethod(HttpMethod.valueOf(requestLineArray[0]));
        request.setRequestUri(requestLineArray[1]);
        request.setHttpVersion(HttpVersion.valueOf(requestLineArray[2]));

        String uri=requestLineArray[1];

        int at = uri.indexOf('?');
        String queryString = uri;
        if (at > 0) {
            queryString = uri.substring(0, at);
            String params = uri.substring(at);
            decodeParamsFromUri(params, request);
        }
        request.setQueryString(queryString);
    }

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

    private static void readHeader(HttpRequest request, String header) {
        String[] kv = splitHeader(header);
        request.addHeader(kv[0], kv[1]);
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

        return true;
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


}
