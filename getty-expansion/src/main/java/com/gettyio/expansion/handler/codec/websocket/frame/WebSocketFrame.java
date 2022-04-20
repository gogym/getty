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
package com.gettyio.expansion.handler.codec.websocket.frame;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.expansion.handler.codec.websocket.frame.PingWebSocketFrame;
import com.gettyio.core.util.ObjectUtil;

import java.math.BigDecimal;

/**
 * WebSocketMessage.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketFrame {
    /**
     * 1000 0000
     */
    public static final byte FIN = (byte) 0x80;
    /**
     * 0111 0000
     */
    public static final byte RSV1 = 0x70;
    /**
     * 0011 0000
     */
    public static final byte RSV2 = 0x30;
    /**
     * 0001 0000
     */
    public static final byte RSV3 = 0x10;
    /**
     * 0000 1111
     */
    public static final byte OPCODE = 0x0F;
    /**
     * 1000 0000
     */
    public static final byte MASK = (byte) 0x80;
    /**
     * 0111 1111
     */
    public static final byte PAYLOAD_LEN = 0x7F;
    public static final byte HAS_EXTEND_DATA = 126;
    public static final byte HAS_EXTEND_DATA_CONTINUE = 127;

    /**
     * 1bit
     */
    private byte fin;
    private byte rsv1 = 0;
    private byte rsv2 = 0;
    private byte rsv3 = 0;
    /**
     * 4bit
     */
    private byte opcode = 1;
    /**
     * 1bit
     */
    private byte mask;
    /**
     * 7bit解决半包时，只读取到消息帧一个字节的情况
     */
    private byte payloadLen = 1;
    /**
     * 16bit
     */
    private short payloadLenExtended = 0;
    /**
     * 64bit
     */
    private long payloadLenExtendedContinued = 0L;
    /**
     * 32bit
     */
    private byte[] maskingKey = null;

    /**
     * 已经读取的消息头字节数量
     */
    private int readCount = 0;
    /**
     * 是否读取完毕，默认否
     */
    private boolean readFinish = false;

    /**
     * 数据帧的头部信息
     */
    private final byte[] headers = new byte[8];
    /**
     * 表示数据长度的字节数量
     */
    private int dataLengthByte = 0;

    /**
     * 已经解析的数据
     */
    private final AutoByteBuffer payloadData = AutoByteBuffer.newByteBuffer();

    public WebSocketFrame() {

    }

    public boolean isReadFinish() {
        return readFinish;
    }

    public void setReadFinish(boolean readFinish) {
        this.readFinish = readFinish;
    }

    public byte getFin() {
        return fin;
    }

    public boolean isMask() {
        return 0 == (mask ^ MASK);
    }

    public void setMask(byte mask) {
        this.mask = mask;
    }

    public byte[] getMaskingKey() {
        return maskingKey;
    }

    public void setMaskingKey(byte... maskingKey) {
        this.maskingKey = maskingKey;
    }

    public byte getOpcode() {
        return opcode;
    }

    public void setOpcode(byte opcode) {
        this.opcode = opcode;
    }

    public byte[] getPayloadData() {
        return payloadData.readableBytesArray();
    }

    public void setPayloadData(byte[] payloadData) {
        this.payloadData.writeBytes(payloadData);
        if (getPayloadLenExtended() > 0 && this.payloadData.readableBytes() == getPayloadLenExtended()) {
            this.readFinish = true;
        } else if (this.payloadData.readableBytes() == getPayloadDataLen()) {
            this.readFinish = true;
        }
    }

    public short getPayloadLenExtended() {
        return payloadLenExtended;
    }

    public long getPayloadLenExtendedContinued() {
        return payloadLenExtendedContinued;
    }

    public byte getRsv1() {
        return rsv1;
    }

    public byte getRsv2() {
        return rsv2;
    }

    public byte getRsv3() {
        return rsv3;
    }

    public long getPayloadDataLen() {

        if (this.payloadLen == HAS_EXTEND_DATA_CONTINUE) {
            return this.getPayloadLenExtendedContinued();
        }
        if (this.payloadLen == HAS_EXTEND_DATA) {
            if (this.getPayloadLenExtended() < 0) {
                return 65535 + 1 + this.getPayloadLenExtended();
            } else {
                return new BigDecimal(this.getPayloadLenExtended()).intValue();
            }
        }
        return this.payloadLen;
    }

    /**
     * 方法名：setPayloadLen
     *
     * @param len 长度
     *            设置数据长度
     */
    public void setPayloadLen(long len) {
        if (len < HAS_EXTEND_DATA) {
            this.payloadLen = (byte) len;
            this.payloadLenExtended = 0;
            this.payloadLenExtendedContinued = 0;
        } else if (len < Short.MAX_VALUE * 2) {
            // 如果数据长度为126到65535(2的16次方)之间，该7位值固定为126
            this.payloadLen = HAS_EXTEND_DATA;
            this.payloadLenExtended = (short) len;
            this.payloadLenExtendedContinued = 0;
        } else {
            //如果数据长度大于65535， 该7位的值固定为127，也就是 1111111
            this.payloadLen = HAS_EXTEND_DATA_CONTINUE;
            this.payloadLenExtended = 0;
            this.payloadLenExtendedContinued = len;
        }
    }

    public byte getPayloadLen() {
        return this.payloadLen;
    }

    /**
     * 方法名：computeCount
     *
     * @param buffer
     * @param count
     * @return int
     * 计算获取数据的长度
     */
    public int computeCount(AutoByteBuffer buffer, int count) {
        return Math.min(buffer.readableBytes(), count);
    }

    /**
     * 解析消息
     *
     * @param buffer
     * @throws Exception
     */
    public void parseMessage(AutoByteBuffer buffer) throws Exception {
        parseMessageHeader(buffer);
        parsePayloadData(buffer);
        if (this.getPayloadDataLen() == this.payloadData.readableBytes()) {
            setReadFinish(true);
            if (isMask()) {
                // 做加密处理
                byte[] bytes = this.payloadData.readableBytesArray();
                for (int i = 0; i < this.getPayloadData().length; i++) {
                    bytes[i] ^= getMaskingKey()[(i % 4)];
                }
                this.payloadData.reset();
                setPayloadData(bytes);
            }
        }
    }

    /**
     * 解析消息头部信息
     *
     * @param buffer 数据
     */
    private void parseMessageHeader(AutoByteBuffer buffer) throws Exception {
        int bt, b2;
        switch (this.readCount) {
            case 0:
                //没有读取过字节
                if (buffer.hasRemaining()) {
                    bt = buffer.read();
                    ++this.readCount;
                    // 后面是否有续帧数据标识
                    this.fin = ((byte) (bt & FIN));
                    // 保留标识1
                    this.rsv1 = ((byte) (bt & RSV1));
                    // 保留标识2
                    this.rsv2 = ((byte) (bt & RSV2));
                    // 保留标识3
                    this.rsv3 = ((byte) (bt & RSV3));
                    //标识数据的格式，以及帧的控制，如：01标识数据内容是 文本，08标识：要求远端去关闭当前连接。
                    this.opcode = ((byte) (bt & OPCODE));
                } else {
                    return;
                }
            case 1:
                //读取过一个字节
                if (buffer.hasRemaining()) {
                    bt = buffer.read();
                    ++this.readCount;
                    // 是否mask标识
                    this.setMask((byte) (bt & MASK));

				/*如果小于126 表示后面的数据长度是 [Payload len] 的值。（最大125byte）
			          等于 126 表示之后的16 bit位的数据值标识数据的长度。（最大65535byte）
			          等于 127 表示之后的64 bit位的数据值标识数据的长度。（一个有符号长整型的最大值）*/
                    this.setPayloadLen(bt & PAYLOAD_LEN);
                } else {
                    return;
                }
            case 2:
                //读取过2个字节
            case 3:
                // read next 16 bit
                if (this.getPayloadLen() == HAS_EXTEND_DATA) {
                    // 数据字节长度为2个字节
                    this.dataLengthByte = 2;
                    // 2个字节 减去（总共读取的字节数-2个字节）
                    int count = this.computeCount(buffer, (2 - (this.readCount - 2)));
                    if (count <= 0) {
                        return;
                    }
                    // 读取2位
                    buffer.readBytes(headers, (this.readCount - 2), count);

                    this.readCount += count;
                    if (this.readCount - 2 >= 2) {
                        bt = headers[0];
                        b2 = headers[1];
                        this.setPayloadLen(ObjectUtil.toLong((byte) bt, (byte) b2));
                    } else {
                        return;
                    }
                }
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                // read next 32 bit
                if (this.getPayloadLen() == HAS_EXTEND_DATA_CONTINUE) {
                    // 数据字节长度为2个字节
                    this.dataLengthByte = 8;
                    // 2个字节 减去（总共读取的字节数-2个字节）
                    int count = this.computeCount(buffer, (8 - (this.readCount - 2)));
                    if (count <= 0) {
                        return;
                    }
                    // 读取2位数字
                    buffer.readBytes(headers, (this.readCount - 2), count);

                    this.readCount += count;
                    if (this.readCount - 2 >= 8) {
                        this.setPayloadLen(ObjectUtil.toLong(headers));
                    } else {
                        return;
                    }
                }
            case 10:
            default:
                if (this.isMask() && maskingKey == null) {
                    // 2个字节 减去（总共读取的字节数-2个字节）
                    int count = this.computeCount(buffer, (4 - (this.readCount - 2 - this.dataLengthByte)));
                    if (count <= 0) {
                        return;
                    }
                    // 读取2位数字
                    buffer.readBytes(headers, (this.readCount - 2 - this.dataLengthByte), count);

                    this.readCount += count;
                    if ((this.readCount - 2 - this.dataLengthByte) >= 4) {
                        this.setMaskingKey(headers[0], headers[1], headers[2], headers[3]);
                    } else {
                        return;
                    }
                }
        }
    }


    /**
     * 解析parsePayloadData
     *
     * @param buffer
     * @throws Exception
     */
    private void parsePayloadData(AutoByteBuffer buffer) {
        if (buffer.hasRemaining()) {
            this.payloadData.writeBytes(buffer);
        }
    }
}
