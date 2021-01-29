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
 * WebSocketConstants.java
 *
 * @description:常量
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketConstants {

    /**
     * 开始字符
     */
    public static final char BEGIN_CHAR = 0x00;
    /**
     * 结束字符
     */
    public static final char END_CHAR = 0xFF;
    /**
     * 消息开始
     */
    public static final String BEGIN_MSG = String.valueOf(BEGIN_CHAR);
    /**
     * 消息结束
     */
    public static final String END_MSG = String.valueOf(END_CHAR);
    /**
     * 空白字符串
     */
    public static final String BLANK = "";

    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final String HEADER_CODE = "iso-8859-1";

    /**
     * 版本0
     */
    public static final int SPLITVERSION0 = 0;
    /**
     * 版本6
     */
    public static final int SPLITVERSION6 = 6;
    /**
     * 版本7
     */
    public static final int SPLITVERSION7 = 7;
    /**
     * 版本8
     */
    public static final int SPLITVERSION8 = 8;
    /**
     * 版本9
     */
    public static final int SPLITVERSION13 = 13;

}
