package com.gettyio.core.handler.codec.websocket;/*
 * 类名：WebSocketDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/30
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.LinkedNonBlockQueue;
import com.gettyio.core.util.ObjectUtil;

import java.util.Arrays;

public class WebSocketDecoder extends ObjectToMessageDecoder {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(SocketChannel.class);
    // 是否已经握手
    static boolean handShak = false;
    //协议版本,默认0
    static String protocolVersion = "0";

    WebSocketMessage messageFrame;

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        if (handShak) {
            // 已经握手处理
            if (Integer.valueOf(protocolVersion) >= WebSocketConstants.SPLITVERSION6) {
                AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer().writeBytes((byte[]) obj);
                //解析数据帧
                byte[] bytes = parserVersion6(autoByteBuffer);
                if (Arrays.equals(ObjectUtil.shortToByte(1001), bytes)) {
                    //1001是ws关闭帧，检测到则关闭连接
                    handShak = false;
                    protocolVersion = "0";
                    return;
                }
                if (bytes != null) {
                    super.decode(socketChannel, bytes, out);
                }
            } else {
                super.decode(socketChannel, obj, out);
            }
        } else {
            // 进行握手处理
            String msg = new String((byte[]) obj, CharsetUtil.UTF_8);
            WebSocketRequest requestInfo = WebSocketHandShak.parserRequest(msg);
            //写出握手信息到客户端
            byte[] bytes = WebSocketHandShak.generateHandshake(requestInfo, socketChannel).getBytes();
            if (socketChannel.getSslHandler() == null) {
                socketChannel.writeToChannel(bytes);
            } else {
                //需要注意的是，当开启了ssl，握手信息需要经过ssl encode之后才能输出给客户端。
                //为了避免握手信息经过其他的encoder，所以直接指定通过sslHandler输出
                socketChannel.getSslHandler().encode(socketChannel, bytes);
            }
            protocolVersion = requestInfo.getSecVersion().toString();
            handShak = true;
        }

    }


    /**
     * 方法名：parser
     *
     * @param buffer
     * @return byte[]
     * 说明：解析版本6以后的数据帧格式
     */
    private byte[] parserVersion6(AutoByteBuffer buffer) throws Exception {
        do {
            if (messageFrame == null) {
                // 没有出现半包
                messageFrame = new WebSocketMessage();
            }
            if (!messageFrame.isReadFinish()) {
                // 读取解析消息头
                messageFrame.parseMessageHeader(buffer);
            }
            int bufferDataLength = buffer.readableBytes();
            int dataLength = bufferDataLength > messageFrame.getDateLength() ? new Long(messageFrame.getDateLength()).intValue() : bufferDataLength;
            byte[] bytes = new byte[dataLength];
            if (dataLength > 0) {
                buffer.readBytes(bytes);
                if (messageFrame.isMask()) {
                    // 做加密处理
                    for (int i = 0; i < dataLength; i++) {
                        bytes[i] ^= messageFrame.getMaskingKey()[(i % 4)];
                    }
                }
                messageFrame.setPayloadData(bytes);
            }

            if (messageFrame.isReadFinish()) {
                bytes = messageFrame.getPayloadData().readableBytesArray();
                messageFrame = null;
                return bytes;
            }

        } while (buffer.hasRemaining());
        return null;
    }


    @Override
    public void channelClosed(SocketChannel aioChannel) throws Exception {
        handShak = false;
        protocolVersion = "0";
        super.channelClosed(aioChannel);
    }
}
