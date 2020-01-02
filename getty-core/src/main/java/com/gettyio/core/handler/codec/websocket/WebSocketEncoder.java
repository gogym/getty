/**
 * <li>文件名：HttpCoder.java
 * <li>说明：
 * <li>创建人：CshBBrain;技术博客：http://cshbbrain.iteye.com/
 * <li>创建日期：2011-11-18
 * <li>修改人：
 * <li>修改日期：
 */
package com.gettyio.core.handler.codec.websocket;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.util.ObjectUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

/**
 * * <pre>
 * version 5+
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |Masking-key, if MASK set to 1  |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 * version 1-4
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |M|R|R|R| opcode|R| Payload len |    Extended payload length    |
 * |O|S|S|S|  (4)  |S|     (7)     |             (16/63)           |
 * |R|V|V|V|       |V|             |   (if payload len==126/127)   |
 * |E|1|2|3|       |4|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |         Extension data        |
 * +-------------------------------+ - - - - - - - - - - - - - - - +
 * :                                                               :
 * +---------------------------------------------------------------+
 * :                       Application data                        :
 * +---------------------------------------------------------------+
 *
 * <li>类型名称：
 * <li>说明：http协议响应编码器
 */
public class WebSocketEncoder extends MessageToByteEncoder {

    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {
        if (WebSocketDecoder.handShak) {
            byte[] bytes;
            if (obj instanceof String) {
                bytes = ((String) obj).getBytes();
            } else {
                bytes = (byte[]) obj;
            }
            if (Integer.valueOf(WebSocketDecoder.protocolVersion) <= WebSocketConstants.SPLITVERSION0) {// 通过0x00,0xff分隔数据
			/*ByteBuffer buffer = ByteBuffer.allocate(msg.getBody().getBytes(Utf8Coder.UTF8).length + 2);
			buffer.put((byte)0x00);
			buffer.put(msg.getBody().getBytes(Utf8Coder.UTF8));
			buffer.put((byte)0xFF);
			msg.setBody(Utf8Coder.decode(buffer));*/
                //log.info("the bg and end : " + Constants.BEGIN_MSG + " : " + Constants.END_MSG);
                String str = new String(bytes, "utf-8");
                String msg = (WebSocketConstants.BEGIN_MSG + str + WebSocketConstants.END_MSG);
                obj = msg.getBytes();
            } else {
                obj = codeVersion6(bytes);
            }
        }
        super.encode(aioChannel, obj);
    }


    /**
     * <li>方法名：codeVersion6
     * <li>@param sockector
     * <li>@param msg
     * <li>返回类型：void
     * <li>说明：对websocket协议进行编码
     * <li>创建人：CshBBrain, 技术博客：http://cshbbrain.iteye.com/
     * <li>创建日期：2012-10-2
     * <li>修改人：
     * <li>修改日期：
     */
    public byte[] codeVersion6(byte[] msg) {

        AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();

        WebSocketMessage messageFrame = new WebSocketMessage();
        messageFrame.setDateLength(msg.length);

        byte[] headers = new byte[2];
        // todo list
        headers[0] = WebSocketMessage.FIN;// 需要调整
        headers[0] |= messageFrame.getRsv1() | messageFrame.getRsv2() | messageFrame.getRsv3() | WebSocketMessage.TXT;
        headers[1] = 0;
        //headers[1] |=  messageFrame.getMask() | messageFrame.getPayloadLen();
        headers[1] |= 0x00 | messageFrame.getPayloadLen();
        autoByteBuffer.writeBytes(headers);// 头部控制信息

        if (messageFrame.getPayloadLen() == WebSocketMessage.HAS_EXTEND_DATA) {// 处理数据长度为126位的情况
            autoByteBuffer.writeBytes(ObjectUtil.shortToByte(messageFrame.getPayloadLenExtended()));
        } else if (messageFrame.getPayloadLen() == WebSocketMessage.HAS_EXTEND_DATA_CONTINUE) {// 处理数据长度为127位的情况
            autoByteBuffer.writeBytes(ObjectUtil.longToByte(messageFrame.getPayloadLenExtendedContinued()));
        }

        if (messageFrame.isMask()) {
            // 做了掩码处理的，需要传递掩码的key
            byte[] keys = messageFrame.getMaskingKey();
            autoByteBuffer.writeBytes(messageFrame.getMaskingKey());

            for (int i = 0; i < autoByteBuffer.array().length; ++i) {// 进行掩码处理
                autoByteBuffer.array()[i] ^= keys[i % 4];
            }
        }
        autoByteBuffer.writeBytes(msg);

        return autoByteBuffer.readableBytesArray();
    }


}
