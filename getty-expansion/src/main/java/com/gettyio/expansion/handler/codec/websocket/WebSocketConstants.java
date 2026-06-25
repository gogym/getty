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

/**
 * WebSocket 协议常量。
 * <p>
 * 包含帧边界字符、协议 GUID、版本号和通道属性键名等定义。
 * </p>
 *
 * @author gogym
 */
public final class WebSocketConstants {

    private WebSocketConstants() {
    }

    /** Hixie-76 帧开始字符 (0x00) */
    public static final char BEGIN_CHAR = 0x00;
    /** Hixie-76 帧结束字符 (0xFF) */
    public static final char END_CHAR = 0xFF;
    /** Hixie-76 帧开始标记 */
    public static final String BEGIN_MSG = String.valueOf(BEGIN_CHAR);
    /** Hixie-76 帧结束标记 */
    public static final String END_MSG = String.valueOf(END_CHAR);
    /** 空白字符串 */
    public static final String BLANK = "";

    /** RFC 6455 握手 GUID，用于计算 Sec-WebSocket-Accept */
    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    /** 握手请求头部编码 */
    public static final String HEADER_CODE = "iso-8859-1";

    /** 协议版本 0（Hixie-76） */
    public static final int SPLIT_VERSION0 = 0;
    /** 协议版本 6 */
    public static final int SPLIT_VERSION6 = 6;
    /** 协议版本 7 */
    public static final int SPLIT_VERSION7 = 7;
    /** 协议版本 8 */
    public static final int SPLIT_VERSION8 = 8;
    /** 协议版本 13（RFC 6455 最终版本） */
    public static final int SPLIT_VERSION13 = 13;

    /** 通道属性键：握手状态 */
    public static final String WEB_SOCKET_HAND_SHAKE = "webSocketHandShake";
    /** 通道属性键：协议版本 */
    public static final String WEB_SOCKET_PROTOCOL_VERSION = "webSocketProtocolVersion";
}
