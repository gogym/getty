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
package com.gettyio.expansion.handler.codec.websocket;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.Base64;
import com.gettyio.core.util.StringUtil;
import com.gettyio.core.util.fastmd5.util.MD5;
import com.gettyio.expansion.handler.codec.http.HttpConstants;
import com.gettyio.expansion.handler.codec.http.HttpException;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * WebSocketHandShak.java
 *
 * @description:握手
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketHandShake {

    protected static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(SocketChannel.class);

    private static final int READ_LINE = 1;
    private static final int READ_HEADERS = 2;
    public static final int READ_CONTENT = 3;
    private static final String SP = " ";


    /**
     * 方法名：parserRequest
     *
     * @param autoByteBuffer 请求字符串
     * @return WebSocketRequest
     * 请求参数进行解析
     */
    public static void parserRequest(AutoByteBuffer autoByteBuffer, WebSocketRequest requestInfo) throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException, HttpException {
        // 解析握手信息
        if (requestInfo.getReadStatus() == 0) {
            requestInfo.setReadStatus(WebSocketHandShake.READ_LINE);
        }

        if (requestInfo.getReadStatus() == WebSocketHandShake.READ_LINE) {
            if (!WebSocketHandShake.readLine(autoByteBuffer, requestInfo)) {
                return;
            }
            requestInfo.setReadStatus(WebSocketHandShake.READ_HEADERS);
        }

        if (requestInfo.getReadStatus() == WebSocketHandShake.READ_HEADERS) {
            if (!WebSocketHandShake.readHeaders(autoByteBuffer, requestInfo)) {
                return;
            }
            requestInfo.setReadStatus(WebSocketHandShake.READ_CONTENT);
        }

    }


    /**
     * 方法名：makeResponseToken
     *
     * @param requestInfo 请求字符串
     * @param token       token
     * @return String
     */
    private static String makeResponseToken(WebSocketRequest requestInfo, byte[] token) {
        MD5 md5 = new MD5();
        for (int i = 0; i < 2; ++i) {
            byte[] asByte = new byte[4];
            long key = (i == 0) ? requestInfo.getKey1().intValue() : requestInfo.getKey2().intValue();
            asByte[0] = (byte) (key >> 24);
            asByte[1] = (byte) ((key << 8) >> 24);
            asByte[2] = (byte) ((key << 16) >> 24);
            asByte[3] = (byte) ((key << 24) >> 24);
            md5.Update(asByte);
        }
        md5.Update(token);
        return md5.asHex();
    }


    /**
     * 方法名：getKey
     *
     * @param key key
     * @return String
     */
    private static String getKey(String key) {
        // CHROME WEBSOCKET VERSION 8中定义的GUID
        String guid = WebSocketConstants.GUID;
        key += guid;
        LOGGER.info(key);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(key.getBytes(WebSocketConstants.HEADER_CODE), 0, key.length());
            byte[] sha1Hash = md.digest();
            key = base64Encode(sha1Hash);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return key;
    }

    /**
     * 转成base64
     *
     * @param input
     * @return
     */
    private static String base64Encode(byte[] input) {
        return Base64.encodeBytes(input);
    }


    /**
     * 方法名：generateHandshake
     *
     * @param requestInfo 请求字符串
     * @param aioChannel  通道
     * @return String
     */
    public static String generateHandshake(WebSocketRequest requestInfo, SocketChannel aioChannel) {
        StringBuilder sb = new StringBuilder();
        if (requestInfo.getSecVersion() < 4) {
            // 版本0--3
            sb.append("HTTP/1.1 101 WebSocket Protocol Handshake").append("\r\n")
                    .append("Upgrade: WebSocket").append("\r\n")
                    .append("Connection: Upgrade").append("\r\n")
                    .append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n");
            if (aioChannel.getSslHandler() == null) {
                sb.append("Sec-WebSocket-Location: ws://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            } else {
                sb.append("Sec-WebSocket-Location: wss://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            }

            if (requestInfo.getCookie() != null) {
                sb.append("cookie: ").append(requestInfo.getCookie()).append("\r\n");
            }
            sb.append("\r\n");
            sb.append(requestInfo.getDigest());
        } else {
            // 大于等于版本4
            sb.append("HTTP/1.1 101 Switching Protocols").append("\r\n")
                    .append("Upgrade: websocket").append("\r\n")
                    .append("Connection: Upgrade").append("\r\n")
                    .append("Sec-WebSocket-Accept: ").append(requestInfo.getDigest()).append("\r\n")
                    .append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n");
            if (aioChannel.getSslHandler() == null) {
                sb.append("Sec-WebSocket-Location: ws://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            } else {
                sb.append("Sec-WebSocket-Location: wss://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            }
            //.append("Sec-WebSocket-Protocol: chat").append("\r\n");
            // 写入换行
            sb.append("\r\n");
        }
        LOGGER.info("the response: " + sb);
        return sb.toString();
    }


    /**
     * 读取请求行
     *
     * @return java.lang.String
     * @params [autoByteBuffer]
     */
    private static boolean readLine(AutoByteBuffer autoByteBuffer, WebSocketRequest webSocketRequest) throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        while (autoByteBuffer.hasRemaining()) {
            byte nextByte = autoByteBuffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = autoByteBuffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    decodeQueryString(webSocketRequest.getSb().toString(), webSocketRequest);
                    webSocketRequest.getSb().setLength(0);
                    return true;
                }
            } else {
                webSocketRequest.getSb().append((char) nextByte);
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
    private static void decodeQueryString(String requestLine, WebSocketRequest webSocketRequest) throws UnsupportedEncodingException {

        String[] requestLineArray = requestLine.split(SP);
        if (requestLineArray.length < 3) {
            webSocketRequest.setReadStatus(READ_LINE);
            return;
        }
        String line = requestLineArray[1];
        webSocketRequest.setRequestUri(line);
        // 如果获取到空行，则读取后面的内容信息
        if (line.equalsIgnoreCase(WebSocketConstants.BLANK)) {
            // 版本0---3放到消息体中的

            // 有发送内容到服务器端
            line += "00000000";
            byte[] token = line.getBytes();
            // 设置签名
            webSocketRequest.setDigest(makeResponseToken(webSocketRequest, token));
        }
    }

    private static boolean readHeaders(AutoByteBuffer buffer, WebSocketRequest webSocketRequest) throws HttpException, AutoByteBuffer.ByteBufferException {

        while (buffer.hasRemaining()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    nextByte = buffer.readByte();
                    readHeader(webSocketRequest, webSocketRequest.getSb().toString());
                    //清空sb
                    webSocketRequest.getSb().setLength(0);

                    if (nextByte == HttpConstants.CR) {
                        nextByte = buffer.readByte();
                        if (nextByte == HttpConstants.LF) {
                            return true;
                        }
                    } else {
                        webSocketRequest.getSb().append((char) nextByte);
                    }
                }
            } else {
                webSocketRequest.getSb().append((char) nextByte);
            }
        }
        return false;
    }

    private static void readHeader(WebSocketRequest webSocketRequest, String header) {
        String[] kv = splitHeader(header);

        String name = kv[0].toLowerCase();
        String value = kv[1].toLowerCase();

        if ("upgrade".equals(name)) {
            if (!"websocket".equals(value)) {
                webSocketRequest.setUpgrade(false);
            }
            webSocketRequest.setUpgrade(true);
        } else if ("connection".equals(name)) {
            if (!"upgrade".equals(value)) {
                LOGGER.info("Wrong value of connection field: " + name);
            }
            webSocketRequest.setConnection(true);
        } else if ("host".equals(name)) {
            webSocketRequest.setHost(value);
        } else if ("origin".equals(name)) {
            webSocketRequest.setOrigin(value);
        } else if (("sec-websocket-key1".equals(name)) || ("sec-websocket-key2".equals(name))) {
            LOGGER.info(name + ":" + value);
            int spaces = 0;
            long number = 0L;
            for (Character c : kv[1].toCharArray()) {
                if (c.equals(' ')) {
                    ++spaces;
                }
                if (Character.isDigit(c)) {
                    number *= 10;
                    number += Character.digit(c, 10);
                }
            }
            number /= spaces;
            webSocketRequest.setKey2(number);
        } else if ("cookie".equals(name)) {
            webSocketRequest.setCookie(value);
        } else if ("sec-websocket-key".equals(name)) {
            // 版本4以及以上放到sec key中设置签名
            webSocketRequest.setDigest(getKey(kv[1]));
        } else if ("sec-websocket-version".equals(name)) {
            //获取安全控制版本设置版本
            webSocketRequest.setSecVersion(Integer.valueOf(value));
        } else if ("sec-websocket-extensions".equals(name)) {
            LOGGER.info(value);
        } else {
            LOGGER.info("Unexpected header field: " + name);
        }
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


}
