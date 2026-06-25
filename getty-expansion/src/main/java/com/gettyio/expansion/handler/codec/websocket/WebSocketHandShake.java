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
import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.Base64;
import com.gettyio.core.util.StringUtil;
import com.gettyio.core.util.MD5;
import com.gettyio.expansion.handler.codec.http.HttpConstants;
import com.gettyio.expansion.handler.codec.http.HttpException;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * WebSocket 握手处理器。
 * <p>
 * 负责解析客户端 HTTP 升级请求，提取 WebSocket 握手字段（Upgrade、Connection、
 * Sec-WebSocket-Key 等），并生成符合 RFC 6455 规范的握手响应。
 * 同时兼容版本 0~3（Hixie-76）和版本 4+（RFC 6455）的握手协议。
 * </p>
 *
 * @author gogym
 */
public class WebSocketHandShake {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(WebSocketHandShake.class);

    /** 解析状态：读取请求行 */
    private static final int READ_LINE = 1;
    /** 解析状态：读取请求头 */
    private static final int READ_HEADERS = 2;
    /** 解析状态：读取完成，可以生成响应 */
    static final int READ_CONTENT = 3;

    /** HTTP 请求行分隔符 */
    private static final String SP = " ";

    /**
     * 解析 WebSocket 握手请求。
     * <p>
     * 按顺序解析请求行和请求头，解析完成后 {@code requestInfo.getReadStatus()}
     * 等于 {@link #READ_CONTENT}，此时可调用 {@link #generateHandshake} 生成响应。
     * 如果数据不完整（半包），方法直接返回，等待更多数据后再次调用。
     * </p>
     *
     * @param buffer     接收到的原始字节数据
     * @param requestInfo 请求信息载体
     */
    public static void parserRequest(AutoByteBuffer buffer, WebSocketRequest requestInfo)
            throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException, HttpException {
        // 初始化解析状态
        if (requestInfo.getReadStatus() == 0) {
            requestInfo.setReadStatus(READ_LINE);
        }

        if (requestInfo.getReadStatus() == READ_LINE) {
            if (!readLine(buffer, requestInfo)) {
                return;
            }
            requestInfo.setReadStatus(READ_HEADERS);
        }

        if (requestInfo.getReadStatus() == READ_HEADERS) {
            if (!readHeaders(buffer, requestInfo)) {
                return;
            }
            requestInfo.setReadStatus(READ_CONTENT);
        }
    }

    /**
     * 生成 Hixie-76（版本 0~3）握手响应令牌。
     * <p>
     * 将 key1、key2 的数值各转为 4 字节大端序，加上 8 字节随机 token，
     * 计算 MD5 摘要作为握手响应。
     * </p>
     *
     * @param requestInfo 握手请求信息
     * @param token       请求行后的 8 字节随机数据
     * @return MD5 十六进制摘要
     */
    private static String makeResponseToken(WebSocketRequest requestInfo, byte[] token) {
        MD5 md5 = new MD5();
        for (int i = 0; i < 2; ++i) {
            byte[] asByte = new byte[4];
            long key = (i == 0) ? requestInfo.getKey1() : requestInfo.getKey2();
            asByte[0] = (byte) (key >> 24);
            asByte[1] = (byte) (key >> 16);
            asByte[2] = (byte) (key >> 8);
            asByte[3] = (byte) key;
            md5.update(asByte);
        }
        md5.update(token);
        return md5.asHex();
    }

    /**
     * 计算 RFC 6455（版本 4+）的 Sec-WebSocket-Accept 值。
     * <p>
     * 将客户端 Sec-WebSocket-Key 拼接 GUID 后做 SHA-1 哈希，再 Base64 编码。
     * </p>
     *
     * @param key 客户端 Sec-WebSocket-Key
     * @return Base64 编码的 Accept 值
     */
    private static String getKey(String key) {
        // RFC 6455 定义的固定 GUID
        key += WebSocketConstants.GUID;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(key.getBytes(WebSocketConstants.HEADER_CODE), 0, key.length());
            byte[] sha1Hash = md.digest();
            return Base64.encodeBytes(sha1Hash);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            LOGGER.error("compute Sec-WebSocket-Accept failed", e);
            return "";
        }
    }

    /**
     * 生成握手响应字符串。
     * <p>
     * 根据 Sec-WebSocket-Version 选择协议版本：
     * <ul>
     *   <li>版本 0~3（Hixie-76）：使用 101 WebSocket Protocol Handshake 格式</li>
     *   <li>版本 4+（RFC 6455）：使用 101 Switching Protocols 格式</li>
     * </ul>
     * </p>
     *
     * @param requestInfo 握手请求信息
     * @param channel     当前通道（用于判断 SSL）
     * @return 握手响应字符串
     */
    public static String generateHandshake(WebSocketRequest requestInfo, AbstractSocketChannel channel) {
        boolean ssl = channel.getSslHandler() != null;
        String scheme = ssl ? "wss://" : "ws://";
        StringBuilder sb = new StringBuilder(256);

        if (requestInfo.getSecVersion() < 4) {
            // Hixie-76（版本 0~3）
            sb.append("HTTP/1.1 101 WebSocket Protocol Handshake\r\n");
            sb.append("Upgrade: WebSocket\r\n");
            sb.append("Connection: Upgrade\r\n");
            sb.append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n");
            sb.append("Sec-WebSocket-Location: ").append(scheme)
                    .append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            if (requestInfo.getCookie() != null) {
                sb.append("cookie: ").append(requestInfo.getCookie()).append("\r\n");
            }
            sb.append("\r\n");
            sb.append(requestInfo.getDigest());
        } else {
            // RFC 6455（版本 4+）
            sb.append("HTTP/1.1 101 Switching Protocols\r\n");
            sb.append("Upgrade: websocket\r\n");
            sb.append("Connection: Upgrade\r\n");
            sb.append("Sec-WebSocket-Accept: ").append(requestInfo.getDigest()).append("\r\n");
            sb.append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n");
            sb.append("Sec-WebSocket-Location: ").append(scheme)
                    .append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            sb.append("\r\n");
        }
        LOGGER.debug("handshake response: {}", sb);
        return sb.toString();
    }

    /**
     * 读取请求行（GET /path HTTP/1.1\r\n）。
     *
     * @return true 表示读取完成，false 表示数据不完整
     */
    private static boolean readLine(AutoByteBuffer buffer, WebSocketRequest request)
            throws AutoByteBuffer.ByteBufferException, UnsupportedEncodingException {
        while (buffer.hasRemaining()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    decodeQueryString(request.getSb().toString(), request);
                    request.getSb().setLength(0);
                    return true;
                }
            } else {
                request.getSb().append((char) nextByte);
            }
        }
        return false;
    }

    /**
     * 解析请求行，提取 URI 和 Hixie-76 版本的 token。
     */
    private static void decodeQueryString(String requestLine, WebSocketRequest request)
            throws UnsupportedEncodingException {
        String[] parts = requestLine.split(SP);
        if (parts.length < 3) {
            request.setReadStatus(READ_LINE);
            return;
        }
        String uri = parts[1];
        request.setRequestUri(uri);
        // Hixie-76：空 URI 时从消息体中提取 token
        if (uri.equalsIgnoreCase(WebSocketConstants.BLANK)) {
            byte[] token = (uri + "00000000").getBytes();
            request.setDigest(makeResponseToken(request, token));
        }
    }

    /**
     * 读取所有请求头，遇到空行（\r\n\r\n）时返回 true。
     *
     * @return true 表示所有头部读取完成，false 表示数据不完整
     */
    private static boolean readHeaders(AutoByteBuffer buffer, WebSocketRequest request)
            throws HttpException, AutoByteBuffer.ByteBufferException {
        while (buffer.hasRemaining()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    readHeader(request, request.getSb().toString());
                    request.getSb().setLength(0);
                    if (!buffer.hasRemaining()) {
                        return false;
                    }
                    nextByte = buffer.readByte();
                    if (nextByte == HttpConstants.CR) {
                        nextByte = buffer.readByte();
                        if (nextByte == HttpConstants.LF) {
                            return true;
                        }
                    } else {
                        request.getSb().append((char) nextByte);
                    }
                }
            } else {
                request.getSb().append((char) nextByte);
            }
        }
        return false;
    }

    /**
     * 解析单个请求头字段，提取 WebSocket 握手相关信息。
     */
    private static void readHeader(WebSocketRequest request, String header) {
        String[] kv = splitHeader(header);
        String name = kv[0].toLowerCase();
        String value = kv[1].toLowerCase();
        request.putHeader(name, value);

        switch (name) {
            case "upgrade":
                // 只有值为 "websocket" 时才认为是有效的升级请求
                request.setUpgrade("websocket".equals(value));
                break;
            case "connection":
                if (!"upgrade".equals(value)) {
                    LOGGER.debug("unexpected connection header value: {}", value);
                }
                request.setConnection(true);
                break;
            case "host":
                request.setHost(value);
                break;
            case "origin":
                request.setOrigin(value);
                break;
            case "sec-websocket-key1":
                request.setKey1(parseHixieKey(kv[1]));
                break;
            case "sec-websocket-key2":
                request.setKey2(parseHixieKey(kv[1]));
                break;
            case "cookie":
                request.setCookie(value);
                break;
            case "sec-websocket-key":
                // RFC 6455（版本 4+）的握手密钥
                request.setDigest(getKey(kv[1]));
                break;
            case "sec-websocket-version":
                request.setSecVersion(Integer.parseInt(value.trim()));
                break;
            case "sec-websocket-extensions":
                LOGGER.debug("websocket extensions: {}", value);
                break;
            default:
                break;
        }
    }

    /**
     * 解析 Hixie-76 版本的 Sec-WebSocket-Key1/Key2。
     * <p>
     * 提取字段中的数字字符组成的整数，除以空格数量，得到实际密钥值。
     * </p>
     *
     * @param rawValue 原始头部值（保留大小写）
     * @return 计算后的密钥值
     */
    private static long parseHixieKey(String rawValue) {
        int spaces = 0;
        long number = 0L;
        for (int i = 0; i < rawValue.length(); i++) {
            char c = rawValue.charAt(i);
            if (c == ' ') {
                ++spaces;
            } else if (c >= '0' && c <= '9') {
                number = number * 10 + (c - '0');
            }
        }
        return spaces > 0 ? number / spaces : number;
    }

    /**
     * 将头部字符串拆分为名称和值。
     *
     * @param header 格式为 "Name: Value" 的头部字符串
     * @return 长度为 2 的数组 [name, value]
     */
    private static String[] splitHeader(String header) {
        final int length = header.length();
        int nameStart = StringUtil.findNonWhitespace(header, 0);
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

        int valueStart = StringUtil.findNonWhitespace(header, colonEnd);
        if (valueStart == length) {
            return new String[]{header.substring(nameStart, nameEnd), ""};
        }

        int valueEnd = StringUtil.findEndOfString(header);
        return new String[]{
                header.substring(nameStart, nameEnd),
                header.substring(valueStart, valueEnd)
        };
    }
}
