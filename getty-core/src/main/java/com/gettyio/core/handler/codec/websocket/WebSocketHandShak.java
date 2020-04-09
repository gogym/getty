/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.handler.codec.websocket;

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
public class WebSocketHandShak {

    protected static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(SocketChannel.class);

    /**
     * 方法名：parserRequest
     *
     * @param requestData 请求字符串
     * @return WebSocketRequest
     * 请求参数进行解析
     */
    public static WebSocketRequest parserRequest(String requestData) {


        // 解析握手信息
        WebSocketRequest requestInfo = new WebSocketRequest();
        String[] requestDatas = requestData.split("\r\n");
        if (requestDatas.length < 0) {
            return null;
        }

        String line = requestDatas[0];
        String[] requestLine = line.split(" ");
        if (requestLine.length < 2) {
            LOGGER.info("Wrong Request-Line format: " + line);
            return null;
        }
        requestInfo.setRequestUri(requestLine[1]);
        for (int i = 1; i < requestDatas.length; ++i) {
            // 解析单条请求信息
            line = requestDatas[i];
            // 如果获取到空行，则读取后面的内容信息
            if (line.equalsIgnoreCase(WebSocketConstants.BLANK)) {
                // 版本0---3放到消息体中的
                if ((i + 1) < requestDatas.length) {
                    // 有发送内容到服务器端
                    line = requestDatas[i + 1] + "00000000";
                    byte[] token = line.getBytes();
                    // 设置签名
                    requestInfo.setDigest(makeResponseToken(requestInfo, token));
                    break;
                }
            }

            String[] parts = line.split(": ", 2);
            if (parts.length != 2) {
                LOGGER.info("Wrong field format: " + line);
                return null;
            }

            String name = parts[0].toLowerCase();
            String value = parts[1].toLowerCase();

            if ("upgrade".equals(name)) {
                if (!"websocket".equals(value)) {
                    LOGGER.info("Wrong value of upgrade field: " + line);
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
                Integer spaces = new Integer(0);
                Long number = new Long(0);
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

                if ("key1".endsWith(name)) {
                    requestInfo.setKey1(number);
                } else {
                    requestInfo.setKey2(number);
                }
            } else if ("cookie".equals(name)) {
                requestInfo.setCookie(value);
            } else if ("sec-websocket-key".equals(name)) {
                // 版本4以及以上放到sec key中
                // 设置签名
                requestInfo.setDigest(getKey(parts[1]));
            } else if ("sec-websocket-version".equals(name)) {
                //获取安全控制版本
                // 设置版本
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
    protected static String makeResponseToken(WebSocketRequest requestInfo, byte[] token) {
        MD5 md5 = new MD5();
        for (Integer i = 0; i < 2; ++i) {
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
    public static String getKey(String key) {
        // CHROME WEBSOCKET VERSION 8中定义的GUID
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        key += guid;
        LOGGER.info(key);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(key.getBytes("iso-8859-1"), 0, key.length());
            byte[] sha1Hash = md.digest();
            key = base64Encode(sha1Hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return key;
    }

    public static String base64Encode(byte[] input) {
        String base64 = Base64.encodeBytes(input);
        return base64;
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
        LOGGER.info("the response: " + sb.toString());
        return sb.toString();
    }
}
