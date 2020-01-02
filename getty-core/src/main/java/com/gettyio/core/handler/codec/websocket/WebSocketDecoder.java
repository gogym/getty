package com.gettyio.core.handler.codec.websocket;/*
 * 类名：WebSocketDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/30
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.LinkedNonBlockQueue;
import com.gettyio.core.util.ObjectUtil;

import java.util.Arrays;

public class WebSocketDecoder extends ObjectToMessageDecoder {

    protected static final InternalLogger log = InternalLoggerFactory.getInstance(AioChannel.class);
    // 是否已经握手
    static boolean handShak = false;
    //协议版本
    static String protocolVersion = "0";

    WebSocketMessage messageFrame;

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        if (handShak) {
            // 已经握手处理
            if (Integer.valueOf(protocolVersion) >= WebSocketConstants.SPLITVERSION6) {
                // 通过0x00,0xff分隔数据
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
                    super.decode(aioChannel, bytes, out);
                }
            } else {
                super.decode(aioChannel, obj, out);
            }
        } else {
            // 进行握手处理
            String msg = new String((byte[]) obj, CharsetUtil.UTF_8);
            WebSocketRequest requestInfo = WebSocketHandShak.parserRequest(msg);
            //写出握手信息到客户端
            aioChannel.writeToChannel(WebSocketHandShak.generateHandshake(requestInfo).getBytes());
            protocolVersion = requestInfo.getSecVersion().toString();
            handShak = true;
        }

    }


    /**
     * <li>方法名：parser
     * <li>@param buffer
     * <li>@param sockector
     * <li>返回类型：void
     * <li>说明：解析版本6以后的数据帧格式
     */
    private byte[] parserVersion6(AutoByteBuffer buffer) throws Exception {
        do {
            if (messageFrame == null) {
                // 没有出现半包的情况
                messageFrame = new WebSocketMessage();
            }
            if (!messageFrame.isReadFinish()) {
                // 读取解析消息头
                messageFrame.parseMessageHeader(buffer);
            }
            int bufferDataLength = buffer.readableBytes();
            int dataLength = bufferDataLength > messageFrame.getDateLength() ? new Long(messageFrame.getDateLength()).intValue() : bufferDataLength;
            byte[] datas = new byte[dataLength];
            if (dataLength > 0) {
                buffer.readBytes(datas);
                if (messageFrame.isMask()) {// 做加密处理
                    for (int i = 0; i < dataLength; i++) {
                        datas[i] ^= messageFrame.getMaskingKey()[(i % 4)];
                    }
                }
                messageFrame.setPayloadData(datas);
            }

            if (messageFrame.isReadFinish()) {
                datas = messageFrame.getPayloadData().readableBytesArray();
                messageFrame = null;
                return datas;
            }

        } while (buffer.hasRemaining());// 处理粘包的情况
        return null;
    }


}
