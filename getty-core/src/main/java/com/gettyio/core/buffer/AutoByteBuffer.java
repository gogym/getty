/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.buffer;

import java.io.IOException;

/**
 * 类名：AutoByteBuffer.java
 * 描述：数组缓存处理类，用于解决需要写入一个未知长度的byte[]，如果使用定长数组的话，处理起来相对麻烦，所以引入一个可自动扩容的数组类。
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AutoByteBuffer {
    public static final int BUFFER_SIZE = 256;
    /**
     * 指针位置，即将读取的位置
     */
    private int readerIndex;
    /**
     * 指针位置，即将写入的位置
     */
    private int writerIndex;
    /**
     * 已储存的数数据
     */
    private byte[] data;


    private AutoByteBuffer(int capacity) {
        data = new byte[capacity];
    }

    /**
     * 获取一个新的实例
     *
     * @return AutoByteBuffer
     */
    public static AutoByteBuffer newByteBuffer() {
        return new AutoByteBuffer(256);
    }

    /**
     * 获取一个新的实例
     *
     * @param capacity 长度
     * @return AutoByteBuffer
     */
    public static AutoByteBuffer newByteBuffer(int capacity) {
        return new AutoByteBuffer(capacity);
    }


    /**
     * 重置指针位置,相当于指定下次开始读取的下标
     * 如果大于写入位置，则可读位置重置为写入位置，readableBytes()结果则为0
     *
     * @param position 下标
     * @return AutoByteBuffer
     */
    public AutoByteBuffer readerIndex(int position) {
        if (position <= writerIndex) {
            readerIndex = position;
        } else {
            readerIndex = writerIndex;
        }
        return this;
    }

    /**
     * 获取未处理byte[]
     *
     * @return byte[]
     */
    public byte[] array() {
        return data;
    }

    /**
     * 清空数据，重置指针
     *
     * @return AutoByteBuffer
     */
    public AutoByteBuffer clear() {
        readerIndex = 0;
        writerIndex = 0;
        data = new byte[BUFFER_SIZE];
        return this;
    }

    /**
     * 清理指针标记，数组内容保留，下次写入会被覆盖，除了array()获取原始数组外无法得到旧数据
     *
     * @return AutoByteBuffer
     */
    public AutoByteBuffer reset() {
        readerIndex = 0;
        writerIndex = 0;
        return this;
    }

    /**
     * 获取剩余未读数据
     *
     * @return byte[]
     */
    public byte[] readableBytesArray() {
        byte[] bytes = new byte[readableBytes()];
        readBytesFromBytes(data, bytes, readerIndex);
        return bytes;
    }

    /**
     * 获取已写入的数据
     *
     * @return byte[]
     */
    public byte[] allWriteBytesArray() {
        byte[] bytes = new byte[writerIndex];
        readBytesFromBytes(data, bytes, 0);
        return bytes;
    }

    /**
     * 删除已读部分，保留未读部分，并重新初始化下标
     */
    public void discardReadBytes() {
        byte[] newBytes = new byte[capacity()];
        int oldReadableBytes = readableBytes();
        //参数意思：原数组，源数组要复制的起始位置，目标数组，目标数组起始位置，要复制的长度
        System.arraycopy(data, readerIndex, newBytes, 0, oldReadableBytes);
        writerIndex = oldReadableBytes;
        readerIndex = 0;
        data = newBytes;
    }

    /**
     * 读取指针位置
     *
     * @return 读取指针位置
     */
    public int readerIndex() {
        return readerIndex;
    }

    /**
     * 写入指针位置
     *
     * @return 写入指针位置
     */
    public int writerIndex() {
        return writerIndex;
    }

    /**
     * 当前可读长度，writerIndex - readerIndex
     *
     * @return 当前可读长度 相当于remaining()
     */
    public int readableBytes() {
        return writerIndex - readerIndex;
    }


    /**
     * 当前是否有可读数据
     *
     * @return 当前是否有可读数据
     */
    public boolean hasRemaining() {
        if ((writerIndex - readerIndex) > 0) {
            return true;
        }
        return false;
    }

    /**
     * 当前剩余可写入数据长度，每次触发扩容后都不一样
     *
     * @return int
     */
    public int writableBytes() {
        return data.length - writerIndex;
    }


    /**
     * 当前容量，当写入数据超过当前容量后会自动扩容
     *
     * @return int
     */
    public int capacity() {
        return data.length;
    }

    /**
     * 读取一个数据到byte，从readIndex位置开始，每读取一个，指针+1，类似byteBuffer的get方法
     *
     * @return int
     * @throws ByteBufferException 抛出异常
     */
    public int read() throws ByteBufferException {
        if (readableBytes() > 0) {
            int i = data[readerIndex];
            readerIndex++;
            return i;
        } else {
            throw new ByteBufferException("readableBytes = 0");
        }
    }

    public byte read(int index) throws ByteBufferException {
        if (writerIndex() > index) {
            byte i = data[index];
            return i;
        } else {
            throw new ByteBufferException("IndexOutOfBoundsException");
        }
    }


    /**
     * 读取数据到byte，1 byte，从readIndex位置开始
     *
     * @return byte
     * @throws ByteBufferException 抛出异常
     */
    public byte readByte() throws ByteBufferException {
        if (readableBytes() > 0) {
            byte i = data[readerIndex];
            readerIndex++;
            return i;
        } else {
            throw new ByteBufferException("readableBytes = 0");
        }
    }

    /**
     * 读取integer值，读4 byte转换为integer，从readIndex位置开始
     *
     * @return int
     * @throws ByteBufferException 抛出异常
     */
    public int readInt() throws ByteBufferException {
        if (readableBytes() >= 4) {
            int result = byteArrayToInt(data, readerIndex);
            readerIndex += 4;
            return result;
        } else {
            throw new ByteBufferException("readableBytes < 4");
        }
    }

    /**
     * 读取数据到bytes，从readIndex位置开始
     *
     * @param bytes 数组
     * @return int
     * @throws ByteBufferException 抛出异常
     */
    public int readBytes(byte[] bytes) throws ByteBufferException {
        if (readableBytes() >= bytes.length) {
            int result = readBytesFromBytes(data, bytes, readerIndex);
            readerIndex += bytes.length;
            return result;
        } else {
            throw new ByteBufferException("readableBytes < " + bytes.length);
        }
    }

    /**
     * 读取数据到bytes，写入从offset到offset+length的区域
     *
     * @param bytes 数组
     * @param offset 下标
     * @param length 长度
     * @return int
     * @throws ByteBufferException 抛出异常
     */
    public int readBytes(byte[] bytes, int offset, int length) throws ByteBufferException {
        if (readableBytes() >= length) {
            int result = readBytesFromBytes(data, bytes, readerIndex, offset, length);
            readerIndex += length;
            return result;
        } else {
            throw new ByteBufferException("readableBytes < " + length);
        }
    }


    /**
     * 读取数据到另一个ByteBuffer
     *
     * @param b 数组
     * @return int
     */
    public int readBytes(AutoByteBuffer b) {
        byte[] bytes = new byte[b.writableBytes()];
        int result = readBytesFromBytes(data, bytes, readerIndex);
        readerIndex += bytes.length;
        b.writeBytes(bytes);
        return result;

    }

    /**
     * 写入Byte数据，1 byte，类似byteBuffer的put
     *
     * @param b 字节
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeByte(byte b) {
        autoExpandCapacity(1);
        data[writerIndex] = b;
        writerIndex++;
        return this;

    }

    /**
     * 写入int值的byte转换结果，即丢弃高位
     *
     * @param b 整数字节
     * @return AutoByteBuffer
     */
    public AutoByteBuffer write(int b) {
        autoExpandCapacity(1);
        data[writerIndex] = (byte) b;
        writerIndex++;
        return this;

    }

    /**
     * 写入integer数据，4 byte
     *
     * @param b 整数字节
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeInt(int b) {
        autoExpandCapacity(4);
        writeBytesToBytes(intToByteArray(b), data, writerIndex);
        writerIndex += 4;
        return this;

    }

    /**
     * 写入数组
     *
     * @param b 写入数据
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeBytes(byte[] b) {
        autoExpandCapacity(b.length);
        writeBytesToBytes(b, data, writerIndex);
        writerIndex += b.length;
        return this;
    }

    /**
     * 写入数组,并指定写入长度
     *
     * @param b          数据
     * @param dataLength 写入长度
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeBytes(byte[] b, int dataLength) {
        autoExpandCapacity(b.length);
        writeBytesToBytes(b, data, writerIndex, dataLength);
        writerIndex += dataLength;
        return this;
    }

    /**
     * 写入一个ByteBuffer可读数据
     *
     * @param b 写入数据
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeBytes(AutoByteBuffer b) {
        int readableBytes = b.readableBytes();
        autoExpandCapacity(readableBytes);
        writeBytesToBytes(b.readableBytesArray(), data, writerIndex);
        b.readerIndex(b.writerIndex);
        writerIndex += readableBytes;
        return this;
    }

    /**
     * 写入一个ByteBuffer可读数据的部分长度
     *
     * @param b          数据
     * @param dataLength 写入长度
     * @return AutoByteBuffer
     */
    public AutoByteBuffer writeBytes(AutoByteBuffer b, int dataLength) {
        autoExpandCapacity(dataLength);
        writeBytesToBytes(b.readableBytesArray(), data, writerIndex, dataLength);
        b.readerIndex(b.readerIndex + dataLength);
        writerIndex += dataLength;
        return this;
    }

    /**
     * 检查写入数据长度，如果不够则扩容,自动扩容,递增值为BUFFER_SIZE的倍数
     *
     * @param addLength 扩容长度
     */
    private void autoExpandCapacity(int addLength) {
        if (writableBytes() < addLength) {
            int newSize = writerIndex + addLength;
            int size = 0;
            while (size < newSize) {
                size += BUFFER_SIZE;
            }
            byte[] newBytes = new byte[size];
            writeBytesToBytes(data, newBytes, 0);
            data = newBytes;
        }
    }

    /**
     * 数组转换成整数型
     *
     * @param b        数组
     * @param position 下标
     */
    private int byteArrayToInt(byte[] b, int position) {
        return b[position + 3] & 0xFF | (b[position + 2] & 0xFF) << 8 | (b[position + 1] & 0xFF) << 16 | (b[position] & 0xFF) << 24;
    }

    /**
     * 数据读取，从一个数组中读取一部分数组
     *
     * @param position 下标
     * @param src      源数组
     * @param result   目标数组
     * @return int
     */
    private int readBytesFromBytes(byte[] src, byte[] result, int position) {
        System.arraycopy(src, position, result, 0, result.length);
        return src.length;
    }

    /**
     * 数据读取，从一个数组中读取一部分数组，写到指定的下标位置
     *
     * @param position 下标
     * @param src      源数组
     * @param result   目标数组
     * @param offst    目标数组起始下标位置
     * @return int
     */
    private int readBytesFromBytes(byte[] src, byte[] result, int position, int offset, int length) {
        System.arraycopy(src, position, result, offset, length);
        return src.length;
    }


    /**
     * 数组复制，向一个数组写入一个数组数组
     *
     * @param src            来源数组
     * @param target         被写入新数据数组
     * @param targetPosition 新数组被写入位置
     * @return AutoByteBuffer
     */
    private AutoByteBuffer writeBytesToBytes(byte[] src, byte[] target, int targetPosition) {
        return writeBytesToBytes(src, target, targetPosition, src.length);
    }

    /**
     * 数组复制，向一个数组写入一个数组数组
     *
     * @param src            来源数组
     * @param target         被写入新数据数组
     * @param targetPosition 新数组被写入位置
     * @return AutoByteBuffer
     */
    private AutoByteBuffer writeBytesToBytes(byte[] src, byte[] target, int targetPosition, int dataLength) {
        System.arraycopy(src, 0, target, targetPosition, dataLength);
        return this;
    }

    /**
     * 整数转换成数组
     *
     * @param i 整数
     * @return byte length=4
     */
    private byte[] intToByteArray(int i) {
        return new byte[]{(byte) ((i >> 24) & 0xFF), (byte) ((i >> 16) & 0xFF), (byte) ((i >> 8) & 0xFF), (byte) (i & 0xFF)};
    }

    public static class ByteBufferException extends IOException {
        ByteBufferException(String message) {
            super(message);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ByteBuffer{");
        sb.append("readerIndex=").append(readerIndex);
        sb.append(", writerIndex=").append(writerIndex);
        sb.append(", capacity=").append(data.length);
        sb.append('}');
        return sb.toString();
    }

}

