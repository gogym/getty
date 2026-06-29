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
import com.gettyio.core.util.ObjectUtil;

/**
 * WebSocket 数据帧。
 * <p>
 * 表示一个符合 RFC 6455 规范的 WebSocket 帧，包含帧头部解析和负载数据提取。
 * 支持文本、二进制、关闭、Ping、Pong 等所有标准帧类型。
 * 子类通过设置 opcode 实现具体帧类型。
 * </p>
 *
 * @author gogym
 */
public class WebSocketFrame {
    /** FIN 标志位：表示帧是否为消息的最后一个分片 (1000 0000) */
    public static final byte FIN = (byte) 0x80;
    /** RSV1 保留位掩码 (0111 0000) */
    public static final byte RSV1 = 0x70;
    /** RSV2 保留位掩码 (0011 0000) */
    public static final byte RSV2 = 0x30;
    /** RSV3 保留位掩码 (0001 0000) */
    public static final byte RSV3 = 0x10;
    /** 操作码掩码 (0000 1111) */
    public static final byte OPCODE = 0x0F;
    /** 掩码标志位掩码 (1000 0000) */
    public static final byte MASK = (byte) 0x80;
    /** 7 位负载长度掩码 (0111 1111) */
    public static final byte PAYLOAD_LEN = 0x7F;
    /** 负载长度需要 16 位扩展字段的标志值 */
    public static final byte HAS_EXTEND_DATA = 126;
    /** 负载长度需要 64 位扩展字段的标志值 */
    public static final byte HAS_EXTEND_DATA_CONTINUE = 127;

    /** FIN 标志位，1 表示消息的最后一个分片 */
    private byte fin;
    /** RSV1 保留位 */
    private byte rsv1 = 0;
    /** RSV2 保留位 */
    private byte rsv2 = 0;
    /** RSV3 保留位 */
    private byte rsv3 = 0;
    /** 操作码（4 位），0=continuation, 1=text, 2=binary, 8=close, 9=ping, 10=pong */
    private byte opcode = 1;
    /** 掩码标志位（1 位） */
    private byte mask;
    /** 7 位负载长度字段 */
    private byte payloadLen = 1;
    /** 16 位扩展长度（当 payloadLen == 126 时使用） */
    private short payloadLenExtended = 0;
    /** 64 位扩展长度（当 payloadLen == 127 时使用） */
    private long payloadLenExtendedContinued = 0L;
    /** 32 位掩码密钥（当 mask=1 时使用） */
    private byte[] maskingKey = null;

    /** 已读取的帧头部字节数（用于半包处理） */
    private int readCount = 0;
    /** 是否读取完毕 */
    private boolean readFinish = false;

    /** 帧头部临时缓冲区（最大 8 字节，用于扩展长度和掩码密钥） */
    private final byte[] headers = new byte[8];
    /** 扩展长度字段的字节数（0、2 或 8） */
    private int dataLengthByte = 0;

    /** 已解析的负载数据 */
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

    /**
     * 获取实际负载数据长度。
     * <p>
     * 根据 7 位 payloadLen 字段的值确定实际长度：
     * <ul>
     *   <li>&lt; 126：直接使用 payloadLen</li>
     *   <li>== 126：使用 16 位扩展长度（无符号 short）</li>
     *   <li>== 127：使用 64 位扩展长度</li>
     * </ul>
     * </p>
     */
    public long getPayloadDataLen() {
        if (this.payloadLen == HAS_EXTEND_DATA_CONTINUE) {
            return this.payloadLenExtendedContinued;
        }
        if (this.payloadLen == HAS_EXTEND_DATA) {
            // 无符号 short 转 int：避免负数问题
            return this.payloadLenExtended & 0xFFFF;
        }
        return this.payloadLen;
    }

    /**
     * 设置负载长度，自动选择合适的编码方式。
     * <ul>
     *   <li>&lt; 126：直接使用 7 位字段</li>
     *   <li>126 ~ 65535：使用 16 位扩展字段</li>
     *   <li>&gt; 65535：使用 64 位扩展字段</li>
     * </ul>
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
     * 计算从缓冲区可读取的字节数（不超过 count）。
     */
    private static int computeCount(AutoByteBuffer buffer, int count) {
        return Math.min(buffer.readableBytes(), count);
    }

    /**
     * 解析帧数据，包括帧头部和负载数据。
     * <p>
     * 支持半包场景，可多次调用直到 {@link #isReadFinish()} 返回 true。
     * 解析完成后，如果帧使用了掩码，会自动解密。
     * </p>
     */
    public void parseMessage(AutoByteBuffer buffer) throws Exception {
        parseMessageHeader(buffer);
        parsePayloadData(buffer);
        if (this.getPayloadDataLen() == this.payloadData.readableBytes()) {
            setReadFinish(true);
            if (isMask()) {
                // 做加密处理：readableBytesArray() 返回拷贝，修改不影响内部数组
                byte[] bytes = this.payloadData.readableBytesArray();
                byte[] maskingKey = getMaskingKey();
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] ^= maskingKey[(i % 4)];
                }
                // 用解密后的数据覆盖缓冲区
                this.payloadData.clear();
                this.payloadData.writeBytes(bytes);
            }
        }
    }

    /**
     * 解析帧头部字段。
     * <p>
     * 利用 switch fall-through 实现状态机：根据已读取字节数跳到对应阶段，
     * 支持半包场景下多次调用。
     * </p>
     */
    @SuppressWarnings("fallthrough")
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
                // fall through: 解析扩展长度字段
            case 3:
                // fall through: 继续解析 16 位扩展长度
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
                // fall through: 解析 64 位扩展长度
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
                // 解析 4 字节掩码密钥
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
     * 读取负载数据到缓冲区（不超过帧声明的负载长度）。
     */
    private void parsePayloadData(AutoByteBuffer buffer) {
        int remaining = (int) this.getPayloadDataLen() - this.payloadData.readableBytes();
        if (remaining > 0 && buffer.hasRemaining()) {
            int toRead = Math.min(remaining, buffer.readableBytes());
            this.payloadData.writeBytes(buffer, toRead);
        }
    }
}
