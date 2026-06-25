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
package com.gettyio.core.buffer;

import com.gettyio.core.util.CharsetUtil;
import com.gettyio.core.util.StringUtil;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * 自动扩容字节缓冲区。
 * <p>
 * 用于写入未知长度的数据，支持自动扩容。采用指数增长策略（倍增），
 * 确保追加操作的均摊时间复杂度为 O(1)。
 * </p>
 *
 * @author gogym
 */
public class AutoByteBuffer {

    /**
     * 默认初始容量
     */
    public static final int BUFFER_SIZE = 256;

    /**
     * 读指针位置
     */
    private int readerIndex;

    /**
     * 写指针位置
     */
    private int writerIndex;

    /**
     * 底层数据存储
     */
    private byte[] data;

    private AutoByteBuffer(int capacity) {
        data = new byte[capacity];
    }

    /**
     * 使用默认容量创建实例
     *
     * @return AutoByteBuffer
     */
    public static AutoByteBuffer newByteBuffer() {
        return new AutoByteBuffer(BUFFER_SIZE);
    }

    /**
     * 使用指定容量创建实例
     *
     * @param capacity 初始容量
     * @return AutoByteBuffer
     */
    public static AutoByteBuffer newByteBuffer(int capacity) {
        return new AutoByteBuffer(capacity);
    }

    /**
     * 清空数据，重置指针。复用已有数组避免重复分配。
     *
     * @return this
     */
    public AutoByteBuffer clear() {
        readerIndex = 0;
        writerIndex = 0;
        // 复用已有数组，不重新分配
        return this;
    }

    /**
     * 重置指针位置，数组内容保留
     */
    public void reset() {
        readerIndex = 0;
        writerIndex = 0;
    }

    /**
     * 是否有已写入数据
     *
     * @return true 如果 writerIndex > 0
     */
    public boolean hasArray() {
        return writerIndex > 0;
    }

    /**
     * 获取底层原始数组
     *
     * @return byte[]
     */
    public byte[] array() {
        return data;
    }

    /**
     * 获取剩余未读数据的拷贝
     *
     * @return byte[]
     */
    public byte[] readableBytesArray() {
        int len = readableBytes();
        byte[] bytes = new byte[len];
        System.arraycopy(data, readerIndex, bytes, 0, len);
        return bytes;
    }

    /**
     * 获取已写入数据的拷贝
     *
     * @return byte[]
     */
    public byte[] allWriteBytesArray() {
        byte[] bytes = new byte[writerIndex];
        System.arraycopy(data, 0, bytes, 0, writerIndex);
        return bytes;
    }

    /**
     * 删除已读部分，将未读数据移动到数组起始位置
     */
    public void discardReadBytes() {
        int readable = readableBytes();
        if (readable > 0 && readerIndex > 0) {
            System.arraycopy(data, readerIndex, data, 0, readable);
        }
        writerIndex = readable;
        readerIndex = 0;
    }

    /**
     * 设置读指针位置
     *
     * @param position 新位置
     * @return this
     */
    public AutoByteBuffer readerIndex(int position) {
        readerIndex = Math.min(position, writerIndex);
        return this;
    }

    /**
     * @return 读指针位置
     */
    public int readerIndex() {
        return readerIndex;
    }

    /**
     * @return 写指针位置
     */
    public int writerIndex() {
        return writerIndex;
    }

    /**
     * @return 当前可读字节数
     */
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    /**
     * @return 是否有可读数据
     */
    public boolean hasRemaining() {
        return writerIndex > readerIndex;
    }

    /**
     * @return 当前可写入字节数
     */
    public int writableBytes() {
        return data.length - writerIndex;
    }

    /**
     * 复制自身（深拷贝）
     *
     * @return 新的 AutoByteBuffer
     */
    public AutoByteBuffer duplicate() {
        AutoByteBuffer dup = new AutoByteBuffer(writerIndex > 0 ? writerIndex : BUFFER_SIZE);
        System.arraycopy(data, 0, dup.data, 0, writerIndex);
        dup.writerIndex = writerIndex;
        dup.readerIndex = readerIndex;
        return dup;
    }

    /**
     * @return 当前底层数组容量
     */
    public int capacity() {
        return data.length;
    }

    /**
     * 跳过指定字节数
     *
     * @param length 跳过的字节数
     * @return this
     */
    public AutoByteBuffer skipBytes(int length) {
        readerIndex += length;
        return this;
    }

    /**
     * 读取一个字节（有符号）
     *
     * @return 字节值
     * @throws ByteBufferException 无可读数据时抛出
     */
    public int read() throws ByteBufferException {
        if (readerIndex >= writerIndex) {
            throw new ByteBufferException("readableBytes = 0");
        }
        return data[readerIndex++];
    }

    /**
     * 读取指定位置的字节
     *
     * @param index 位置
     * @return 字节值
     * @throws ByteBufferException 越界时抛出
     */
    public byte read(int index) throws ByteBufferException {
        if (index >= writerIndex) {
            throw new ByteBufferException("IndexOutOfBoundsException");
        }
        return data[index];
    }

    /**
     * 读取一个字节
     *
     * @return 字节值
     * @throws ByteBufferException 无可读数据时抛出
     */
    public byte readByte() throws ByteBufferException {
        if (readerIndex >= writerIndex) {
            throw new ByteBufferException("readableBytes = 0");
        }
        return data[readerIndex++];
    }

    /**
     * 读取一个无符号字节
     *
     * @return 无符号字节值 (0~255)
     * @throws ByteBufferException 无可读数据时抛出
     */
    public short readUnsignedByte() throws ByteBufferException {
        return (short) (readByte() & 0xFF);
    }

    /**
     * 读取 4 字节整数（大端序）
     *
     * @return int 值
     * @throws ByteBufferException 可读数据不足 4 字节时抛出
     */
    public int readInt() throws ByteBufferException {
        if (readableBytes() < 4) {
            throw new ByteBufferException("readableBytes < 4");
        }
        int result = (data[readerIndex] & 0xFF) << 24
                | (data[readerIndex + 1] & 0xFF) << 16
                | (data[readerIndex + 2] & 0xFF) << 8
                | (data[readerIndex + 3] & 0xFF);
        readerIndex += 4;
        return result;
    }

    /**
     * 读取数据到字节数组
     *
     * @param bytes 目标数组
     * @return 源数组长度
     * @throws ByteBufferException 可读数据不足时抛出
     */
    public int readBytes(byte[] bytes) throws ByteBufferException {
        if (readableBytes() < bytes.length) {
            throw new ByteBufferException("readableBytes < " + bytes.length);
        }
        System.arraycopy(data, readerIndex, bytes, 0, bytes.length);
        readerIndex += bytes.length;
        return data.length;
    }

    /**
     * 读取数据到字节数组的指定区域
     *
     * @param bytes  目标数组
     * @param offset 目标数组起始偏移
     * @param length 读取长度
     * @return 源数组长度
     * @throws ByteBufferException 可读数据不足时抛出
     */
    public int readBytes(byte[] bytes, int offset, int length) throws ByteBufferException {
        if (readableBytes() < length) {
            throw new ByteBufferException("readableBytes < " + length);
        }
        System.arraycopy(data, readerIndex, bytes, offset, length);
        readerIndex += length;
        return data.length;
    }

    /**
     * 读取数据到另一个 AutoByteBuffer
     *
     * @param b 目标缓冲区
     * @return 源数组长度
     */
    public int readBytes(AutoByteBuffer b) {
        int len = readableBytes();
        b.writeBytes(data, readerIndex, len);
        readerIndex += len;
        return data.length;
    }

    /**
     * 读取指定长度的数据，返回新的 AutoByteBuffer
     *
     * @param len 读取长度
     * @return 新的 AutoByteBuffer
     * @throws ByteBufferException 可读数据不足时抛出
     */
    public AutoByteBuffer readRetainedSlice(int len) throws ByteBufferException {
        if (readableBytes() < len) {
            throw new ByteBufferException("readableBytes < " + len);
        }
        AutoByteBuffer b = new AutoByteBuffer(len);
        System.arraycopy(data, readerIndex, b.data, 0, len);
        b.writerIndex = len;
        readerIndex += len;
        return b;
    }

    /**
     * 写入单个字节
     *
     * @param b 字节值
     * @return this
     */
    public AutoByteBuffer writeByte(byte b) {
        ensureWritable(1);
        data[writerIndex++] = b;
        return this;
    }

    /**
     * 写入单个字节（取 int 的低 8 位）
     *
     * @param b 整数值
     * @return this
     */
    public AutoByteBuffer write(int b) {
        ensureWritable(1);
        data[writerIndex++] = (byte) b;
        return this;
    }

    /**
     * 写入 4 字节整数（大端序）
     *
     * @param b 整数值
     * @return this
     */
    public AutoByteBuffer writeInt(int b) {
        ensureWritable(4);
        data[writerIndex] = (byte) (b >>> 24);
        data[writerIndex + 1] = (byte) (b >>> 16);
        data[writerIndex + 2] = (byte) (b >>> 8);
        data[writerIndex + 3] = (byte) b;
        writerIndex += 4;
        return this;
    }

    /**
     * 写入 2 字节 short（大端序）
     *
     * @param value short 值
     * @return this
     */
    public AutoByteBuffer writeShort(int value) {
        ensureWritable(2);
        data[writerIndex] = (byte) (value >>> 8);
        data[writerIndex + 1] = (byte) value;
        writerIndex += 2;
        return this;
    }

    /**
     * 写入整个字节数组
     *
     * @param b 数据
     * @return this
     */
    public AutoByteBuffer writeBytes(byte[] b) {
        ensureWritable(b.length);
        System.arraycopy(b, 0, data, writerIndex, b.length);
        writerIndex += b.length;
        return this;
    }

    /**
     * 写入字节数组的指定长度
     *
     * @param b   数据
     * @param len 写入长度
     * @return this
     */
    public AutoByteBuffer writeBytes(byte[] b, int len) {
        ensureWritable(len);
        System.arraycopy(b, 0, data, writerIndex, len);
        writerIndex += len;
        return this;
    }

    /**
     * 写入字节数组的指定区域
     *
     * @param src      源数组
     * @param srcIndex 源数组起始位置
     * @param len      写入长度
     * @return this
     */
    public AutoByteBuffer writeBytes(byte[] src, int srcIndex, int len) {
        ensureWritable(len);
        System.arraycopy(src, srcIndex, data, writerIndex, len);
        writerIndex += len;
        return this;
    }

    /**
     * 写入另一个 AutoByteBuffer 的可读数据
     *
     * @param b 源缓冲区
     * @return this
     */
    public AutoByteBuffer writeBytes(AutoByteBuffer b) {
        int len = b.readableBytes();
        ensureWritable(len);
        System.arraycopy(b.data, b.readerIndex, data, writerIndex, len);
        b.readerIndex = b.writerIndex;
        writerIndex += len;
        return this;
    }

    /**
     * 写入另一个 AutoByteBuffer 的指定长度数据
     *
     * @param b          源缓冲区
     * @param dataLength 写入长度
     * @return this
     */
    public AutoByteBuffer writeBytes(AutoByteBuffer b, int dataLength) {
        ensureWritable(dataLength);
        System.arraycopy(b.data, b.readerIndex, data, writerIndex, dataLength);
        b.readerIndex += dataLength;
        writerIndex += dataLength;
        return this;
    }

    // ======================== 内部方法 ========================

    /**
     * 确保可写入空间足够。采用指数增长策略（倍增），均摊 O(1)。
     *
     * @param needed 需要的字节数
     */
    private void ensureWritable(int needed) {
        int available = data.length - writerIndex;
        if (available >= needed) {
            return;
        }
        // 指数增长：每次至少翻倍，直到满足需求
        int newCapacity = data.length;
        int minCapacity = writerIndex + needed;
        while (newCapacity < minCapacity) {
            newCapacity <<= 1;
            // 防溢出
            if (newCapacity < 0) {
                newCapacity = minCapacity;
                break;
            }
        }
        byte[] newData = new byte[newCapacity];
        System.arraycopy(data, 0, newData, 0, writerIndex);
        data = newData;
    }

    /**
     * 解析为字符串
     *
     * @param index   起始位置
     * @param length  长度
     * @param charset 字符集
     * @return 字符串
     */
    public String decodeString(int index, int length, Charset charset) {
        return decodeString(this, index, length, charset);
    }

    /**
     * 从指定 AutoByteBuffer 解析字符串
     *
     * @param src         源缓冲区
     * @param readerIndex 起始位置
     * @param len         长度
     * @param charset     字符集
     * @return 字符串
     */
    public String decodeString(AutoByteBuffer src, int readerIndex, int len, Charset charset) {
        if (len == 0) {
            return StringUtil.EMPTY_STRING;
        }
        byte[] srcArray = src.data;
        if (CharsetUtil.US_ASCII.equals(charset)) {
            return new String(srcArray, 0, readerIndex, len);
        }
        return new String(srcArray, readerIndex, len, charset);
    }

    @Override
    public String toString() {
        return "ByteBuffer{readerIndex=" + readerIndex
                + ", writerIndex=" + writerIndex
                + ", capacity=" + data.length + '}';
    }

    /**
     * 缓冲区操作异常
     */
    public static class ByteBufferException extends IOException {
        ByteBufferException(String message) {
            super(message);
        }
    }
}
