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

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.util.ObjectUtil;

/**
 * WebSocketMessage.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class WebSocketMessage {
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
    public static final byte PAYLOADLEN = 0x7F;
    public static final byte HAS_EXTEND_DATA = 126;
    public static final byte HAS_EXTEND_DATA_CONTINUE = 127;

    /**
     * 0000 0001
     */
    public static final byte TXT = 0x01;
    /**
     * 0000 1000
     */
    public static final byte CLOSE = 0x08;

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
    private byte[] headers = new byte[8];
    /**
     * 表示数据长度的字节数量
     */
    private int dataLengthByte = 0;

    /**
     * 已经解析的数据
     */
    private AutoByteBuffer payloadData = AutoByteBuffer.newByteBuffer();

    public WebSocketMessage() {

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

    public void setFin(byte fin) {
        this.fin = fin;
    }

    public byte getMask() {
        return mask;
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

    public AutoByteBuffer getPayloadData() {
        return payloadData;
    }

    public void setPayloadData(byte[] payloadData) {
        this.payloadData.writeBytes(payloadData);
        if (getPayloadLenExtended() > 0 && this.payloadData.readableBytes() == getPayloadLenExtended()) {
            this.readFinish = true;
        } else if (this.payloadData.readableBytes() == getPayloadLen()) {
            this.readFinish = true;
        }
    }

    public byte getPayloadLen() {
        return payloadLen;
    }

    public void setPayloadLen(byte payloadLen) {
        this.payloadLen = payloadLen;
    }

    public short getPayloadLenExtended() {
        return payloadLenExtended;
    }

    public void setPayloadLenExtended(short payloadLenExtended) {
        this.payloadLenExtended = payloadLenExtended;
    }

    public long getPayloadLenExtendedContinued() {
        return payloadLenExtendedContinued;
    }

    public void setPayloadLenExtendedContinued(long payloadLenExtendedContinued) {
        this.payloadLenExtendedContinued = payloadLenExtendedContinued;
    }

    public byte getRsv1() {
        return rsv1;
    }

    public void setRsv1(byte rsv1) {
        this.rsv1 = rsv1;
    }

    public byte getRsv2() {
        return rsv2;
    }

    public void setRsv2(byte rsv2) {
        this.rsv2 = rsv2;
    }

    public byte getRsv3() {
        return rsv3;
    }

    public void setRsv3(byte rsv3) {
        this.rsv3 = rsv3;
    }

    /**
     * <li>方法名：getDateLength
     * <li>@return
     * <li>返回类型：long
     * <li>说明：获取数据长度
     */
    public long getDateLength() {
        if (this.getPayloadLenExtendedContinued() > 0) {
            return this.getPayloadLenExtendedContinued();
        }

        if (this.getPayloadLenExtended() > 0) {
            return this.getPayloadLenExtended();
        }
        if (this.getPayloadLen() == HAS_EXTEND_DATA || this.getPayloadLen() == HAS_EXTEND_DATA_CONTINUE) {
            return 0L;
        }

        return this.getPayloadLen();
    }

    /**
     * 方法名：setDateLength
     *
     * @param len 长度
     *            设置数据长度
     */
    public void setDateLength(long len) {
        if (len < HAS_EXTEND_DATA) {
            this.payloadLen = (byte) len;
            this.payloadLenExtended = 0;
            this.payloadLenExtendedContinued = 0;
        } else if (len < 1 * Short.MAX_VALUE * 2) {
            // UNSIGNED
            this.payloadLen = HAS_EXTEND_DATA;
            this.payloadLenExtended = (short) len;
            this.payloadLenExtendedContinued = 0;
        } else {
            this.payloadLen = HAS_EXTEND_DATA_CONTINUE;
            this.payloadLenExtended = 0;
            this.payloadLenExtendedContinued = len;
        }
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
        return (buffer.readableBytes() >= count) ? count : buffer.readableBytes();
    }

    /**
     * 方法名：parseMessageHeader
     *
     * @param buffer 数据
     *               解析消息头部信息
     */
    public void parseMessageHeader(AutoByteBuffer buffer) throws Exception {
        int bt, b2;

        switch (this.readCount) {
            case 0:
                //没有读取过字节
                if (buffer.hasRemaining()) {
                    bt = buffer.read();
                    ++this.readCount;
                    // 后面是否有续帧数据标识
                    this.setFin((byte) (bt & FIN));
                    // 保留标识1
                    this.setRsv1((byte) (bt & RSV1));
                    // 保留标识2
                    this.setRsv2((byte) (bt & RSV2));
                    // 保留标识3
                    this.setRsv3((byte) (bt & RSV3));
                    //标识数据的格式，以及帧的控制，如：01标识数据内容是 文本，08标识：要求远端去关闭当前连接。
                    this.setOpcode((byte) (bt & OPCODE));
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
                    this.setDateLength(bt & PAYLOADLEN);// 数据长度位数
                } else {
                    return;
                }
            case 2:
                //读取过2个字节
            case 3:
                // read next 16 bit
                if (this.getDateLength() == HAS_EXTEND_DATA) {
                    // 数据字节长度为2个字节
                    this.dataLengthByte = 2;
                    // 2个字节 减去（总共读取的字节数-2个字节）
                    int count = this.computeCount(buffer, (2 - (this.readCount - 2)));
                    if (count <= 0) {
                        return;
                    }
                    // 读取2位数字
                    buffer.readBytes(headers, (this.readCount - 2), count);

                    this.readCount += count;
                    if (this.readCount - 2 >= 2) {
                        bt = headers[0];
                        b2 = headers[1];
                        this.setDateLength(ObjectUtil.toLong((byte) bt, (byte) b2));
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
                if (this.getDateLength() == HAS_EXTEND_DATA_CONTINUE) {
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
                        this.setDateLength(ObjectUtil.toLong(headers));
                    } else {
                        return;
                    }
                }
            case 10:
            default:
                if (this.isMask()) {
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
                        //this.readFinish = true;
                    } else {
                        return;
                    }
                }
        }
    }
}
