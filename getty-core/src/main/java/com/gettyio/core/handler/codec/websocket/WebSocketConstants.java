
package com.gettyio.core.handler.codec.websocket;

public class WebSocketConstants {

    public static final char BEGIN_CHAR = 0x00;// 开始字符
    public static final char END_CHAR = 0xFF;// 结束字符
    public static final String BEGIN_MSG = String.valueOf(BEGIN_CHAR);// 消息开始
    public static final String END_MSG = String.valueOf(END_CHAR); // 消息结束
    public static final String BLANK = "";// 空白字符串

    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final String HEADER_CODE = "iso-8859-1";
    public static final int SPLITVERSION0 = 0;// 版本0
    public static final int SPLITVERSION6 = 6;// 版本6
    public static final int SPLITVERSION7 = 7;// 版本7
    public static final int SPLITVERSION8 = 8;// 版本8
    public static final int SPLITVERSION13 = 13;// 版本13

}
