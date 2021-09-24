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

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.Base64;
import com.gettyio.core.util.fastmd5.util.MD5;

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

    /**
     * 是否已经握手
     */
    private static boolean handShake = false;

    public static boolean isHandShake() {
        return handShake;
    }

    public static void setHandShake(boolean handShake) {
        WebSocketHandShake.handShake = handShake;
    }

    /**
     * 方法名：parserRequest
     *
     * @param request 请求字符串
     * @return WebSocketRequest
     * 请求参数进行解析
     */
    public static WebSocketRequest parserRequest(String request) {

        // 解析握手信息
        WebSocketRequest requestInfo = new WebSocketRequest();
        String[] requestData = request.split("\r\n");

        String line = requestData[0];
        String[] requestLine = line.split(" ");
        if (requestLine.length < 3) {
            return null;
        }
        requestInfo.setRequestUri(requestLine[1]);
        for (int i = 1; i < requestData.length; ++i) {
            // 解析单条请求信息
            line = requestData[i];
            // 如果获取到空行，则读取后面的内容信息
            if (line.equalsIgnoreCase(WebSocketConstants.BLANK)) {
                // 版本0---3放到消息体中的
                if ((i + 1) < requestData.length) {
                    // 有发送内容到服务器端
                    line = requestData[i + 1] + "00000000";
                    byte[] token = line.getBytes();
                    // 设置签名
                    requestInfo.setDigest(makeResponseToken(requestInfo, token));
                    break;
                }
            }

            //读取请求头
            String[] parts = line.split(": ", 2);
            if (parts.length != 2) {
                return null;
            }
            String name = parts[0].toLowerCase();
            String value = parts[1].toLowerCase();

            if ("upgrade".equals(name)) {
                if (!"websocket".equals(value)) {
                    return null;
                }
                requestInfo.setUpgrade(true);
            } else if ("connection".equals(name)) {
                if (!"upgrade".equals(value)) {
                    LOGGER.info("Wrong value of connection field: " + line);
                }
                requestInfo.setConnection(true);
            } else if ("host".equals(name)) {
                requestInfo.setHost(value);
            } else if ("origin".equals(name)) {
                requestInfo.setOrigin(value);
            } else if (("sec-websocket-key1".equals(name)) || ("sec-websocket-key2".equals(name))) {
                LOGGER.info(name + ":" + value);
                int spaces = 0;
                long number = 0L;
                for (Character c : parts[1].toCharArray()) {
                    if (c.equals(' ')) {
                        ++spaces;
                    }
                    if (Character.isDigit(c)) {
                        number *= 10;
                        number += Character.digit(c, 10);
                    }
                }
                number /= spaces;
                requestInfo.setKey2(number);
            } else if ("cookie".equals(name)) {
                requestInfo.setCookie(value);
            } else if ("sec-websocket-key".equals(name)) {
                // 版本4以及以上放到sec key中设置签名
                requestInfo.setDigest(getKey(parts[1]));
            } else if ("sec-websocket-version".equals(name)) {
                //获取安全控制版本设置版本
                requestInfo.setSecVersion(Integer.valueOf(value));
            } else if ("sec-websocket-extensions".equals(name)) {
                LOGGER.info(value);
            } else {
                LOGGER.info("Unexpected header field: " + line);
            }
        }
        return requestInfo;
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
}
