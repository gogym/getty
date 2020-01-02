package com.gettyio.core.handler.codec.websocket;/*
 * 类名：WebSocketHandShak
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/2
 */

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import sun.misc.BASE64Encoder;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WebSocketHandShak {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(AioChannel.class);

    /**
     * <li>方法名：parserRequest
     * <li>@param str
     * <li>@param requestInfo
     * <li>返回类型：void
     * <li>说明：对请求参数进行解析
     *
     * @throws UnsupportedEncodingException
     */
    public static WebSocketRequest parserRequest(String requestData) throws UnsupportedEncodingException {
        // 解析握手信息
        WebSocketRequest requestInfo = new WebSocketRequest();
        String[] requestDatas = requestData.split("\r\n");

        if (requestDatas.length < 0) {
            return null;
        }

        String line = requestDatas[0];
        String[] requestLine = line.split(" ");
        if (requestLine.length < 2) {
            log.info("Wrong Request-Line format: " + line);
            return null;
        }
        requestInfo.setRequestUri(requestLine[1]);
        for (int i = 1; i < requestDatas.length; ++i) {
            // 解析单条请求信息
            line = requestDatas[i];
            // 如果获取到空行，则读取后面的内容信息
            if (line.equalsIgnoreCase(WebSocketConstants.BLANK)) {// 版本0---3放到消息体中的
                if ((i + 1) < requestDatas.length) {// 有发送内容到服务器端
                    line = requestDatas[i + 1] + "00000000";
                    byte[] token = line.getBytes();//.substring(0, 8).getBytes(Utf8Coder.UTF8);
                    try {
                        requestInfo.setDigest(makeResponseToken(requestInfo, token));// 设置签名
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            String[] parts = line.split(": ", 2);
            if (parts.length != 2) {
                log.info("Wrong field format: " + line);
                return null;
            }

            String name = parts[0].toLowerCase();
            String value = parts[1].toLowerCase();

            if (name.equals("upgrade")) {
                if (!value.equals("websocket")) {
                    log.info("Wrong value of upgrade field: " + line);
                    return null;
                }
                requestInfo.setUpgrade(true);
            } else if (name.equals("connection")) {
                if (!value.equals("upgrade")) {
                    log.info("Wrong value of connection field: " + line);
                }
                requestInfo.setConnection(true);
            } else if (name.equals("host")) {
                requestInfo.setHost(value);
            } else if (name.equals("origin")) {
                requestInfo.setOrigin(value);
            } else if ((name.equals("sec-websocket-key1")) || (name.equals("sec-websocket-key2"))) {
                log.info(name + ":" + value);
                Integer spaces = new Integer(0);
                Long number = new Long(0);
                for (Character c : parts[1].toCharArray()) {
                    if (c.equals(' '))
                        ++spaces;
                    if (Character.isDigit(c)) {
                        number *= 10;
                        number += Character.digit(c, 10);
                    }
                }
                number /= spaces;

                if (name.endsWith("key1")) {
                    requestInfo.setKey1(number);
                } else {
                    requestInfo.setKey2(number);
                }
            } else if (name.equals("cookie")) {
                requestInfo.setCookie(value);
            } else if (name.equals("sec-websocket-key")) {// 版本4以及以上放到sec key中
                requestInfo.setDigest(getKey(parts[1]));// 设置签名
            } else if (name.equals("sec-websocket-version")) {//获取安全控制版本
                requestInfo.setSecVersion(Integer.valueOf(value));// 设置版本
            } else if (name.equals("sec-websocket-extensions")) {//获取安全控制版本
                log.info(value);
            } else {
                log.info("Unexpected header field: " + line);
            }
        }
        return requestInfo;
    }


    /**
     * <li>方法名：makeResponseToken
     * <li>@param requestInfo
     * <li>@param token
     * <li>@return
     * <li>@throws NoSuchAlgorithmException
     */
    protected static String makeResponseToken(WebSocketRequest requestInfo, byte[] token) throws NoSuchAlgorithmException {
        MessageDigest md5digest = MessageDigest.getInstance("MD5");
        for (Integer i = 0; i < 2; ++i) {
            byte[] asByte = new byte[4];
            long key = (i == 0) ? requestInfo.getKey1().intValue() : requestInfo.getKey2().intValue();
            asByte[0] = (byte) (key >> 24);
            asByte[1] = (byte) ((key << 8) >> 24);
            asByte[2] = (byte) ((key << 16) >> 24);
            asByte[3] = (byte) ((key << 24) >> 24);
            md5digest.update(asByte);
        }
        md5digest.update(token);
        return new String(md5digest.digest());
    }


    /**
     * <li>方法名：getKey
     * <li>@param key
     * <li>@return
     * <li>返回类型：String
     */
    public static String getKey(String key) {
        // CHROME WEBSOCKET VERSION 8中定义的GUID，详细文档地址：http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-10
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        key += guid;
        log.info(key);
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
        BASE64Encoder encoder = new BASE64Encoder();
        String base64 = encoder.encode(input);
        return base64;
    }


    /**
     * <li>方法名：generateHandshake
     * <li>@param requestInfo
     * <li>@throws UnsupportedEncodingException
     * <li>返回类型：void
     */
    public static String generateHandshake(WebSocketRequest requestInfo) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (requestInfo.getSecVersion() < 4) {// 版本0--3
            sb.append("HTTP/1.1 101 WebSocket Protocol Handshake").append("\r\n")
                    .append("Upgrade: WebSocket").append("\r\n")
                    .append("Connection: Upgrade").append("\r\n")
                    .append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n")
                    .append("Sec-WebSocket-Location: ws://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");

            if (requestInfo.getCookie() != null) {
                sb.append("cookie: ").append(requestInfo.getCookie()).append("\r\n");
            }

            sb.append("\r\n"); // 写入空行

            sb.append(requestInfo.getDigest());
            //ByteBuffer buffer = ByteBuffer.allocate(sb.length() + requestInfo.getDigest().length);
            //buffer.put(sb.toString().getBytes(Utf8Coder.UTF8)).put(requestInfo.getDigest());
        } else {// 大于等于版本4
            sb.append("HTTP/1.1 101 Switching Protocols").append("\r\n")
                    .append("Upgrade: websocket").append("\r\n")
                    .append("Connection: Upgrade").append("\r\n")
                    .append("Sec-WebSocket-Accept: ").append(requestInfo.getDigest()).append("\r\n")
                    .append("Sec-WebSocket-Origin: ").append(requestInfo.getOrigin()).append("\r\n")
                    .append("Sec-WebSocket-Location: ws://").append(requestInfo.getHost()).append(requestInfo.getRequestUri()).append("\r\n");
            //.append("Sec-WebSocket-Protocol: chat").append("\r\n");

            sb.append("\r\n"); // 写入空行
        }
        log.info("the response: " + sb.toString());

        return sb.toString();
    }
}
