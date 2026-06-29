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
package com.gettyio.core.buffer.pool;

import java.nio.ByteBuffer;

/**
 * 可引用计数的字节缓冲区。
 * <p>
 * 封装底层 {@link ByteBuffer}，提供以下核心能力：
 * <ul>
 *   <li><b>双指针模型</b>：独立的 {@code readerIndex} / {@code writerIndex}，
 *       支持顺序读写而不依赖 ByteBuffer 的 position/limit</li>
 *   <li><b>便捷读写 API</b>：readByte、readShort、readInt、writeByte、writeShort 等</li>
 *   <li><b>零拷贝切片</b>：{@link #slice()} 返回共享底层数据的子缓冲区</li>
 *   <li><b>引用计数</b>：子类（{@link PooledByteBuffer}）通过 retain/release 管理生命周期</li>
 *   <li><b>I/O 兼容</b>：{@link #flipToFill()} / {@link #flipToFlush()} 保持与通道层的兼容</li>
 * </ul>
 * </p>
 *
 * <p>双指针模型说明：
 * <pre>
 *   +-------------------+------------------+------------------+
 *   | 0 <= readerIndex  | readerIndex <=   | writerIndex <=   |
 *   |                   | writerIndex      | capacity         |
 *   +-------------------+------------------+------------------+
 *   |   已读（discard）  |   可读数据        |   可写空间        |
 *   +-------------------+------------------+------------------+
 * </pre>
 * </p>
 *
 * @author Getty Project
 * @see PooledByteBuffer
 * @see ByteBufferPool
 */
class RetainableByteBuffer {

    /** 底层 ByteBuffer 实例 */
    private final ByteBuffer buffer;

    /** 读指针位置 */
    private int readerIndex;

    /** 写指针位置 */
    private int writerIndex;

    /**
     * 构造 RetainableByteBuffer。
     *
     * @param buffer   底层 ByteBuffer
     * @param releaser 保留参数，兼容子类构造调用，当前未使用
     */
    RetainableByteBuffer(ByteBuffer buffer, java.util.function.Consumer<RetainableByteBuffer> releaser) {
        this.buffer = buffer;
        this.readerIndex = 0;
        this.writerIndex = 0;
    }

    /**
     * 使用指定 ByteBuffer 构造实例（切片场景使用）。
     *
     * @param buffer 底层 ByteBuffer（通常是 slice/duplicate）
     */
    RetainableByteBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        this.readerIndex = buffer.position();
        this.writerIndex = buffer.limit();
    }

    // ======================== 底层 ByteBuffer 访问 ========================

    /**
     * 获取底层的 ByteBuffer 对象。
     *
     * @return ByteBuffer 对象
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    // ======================== 双指针模型 ========================

    /**
     * 获取读指针位置。
     *
     * @return 当前 readerIndex
     */
    public int readerIndex() {
        return readerIndex;
    }

    /**
     * 设置读指针位置。
     *
     * @param readerIndex 新位置，不超过 writerIndex
     * @return this
     */
    public RetainableByteBuffer readerIndex(int readerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException(
                    "readerIndex: " + readerIndex + " (expected: 0 <= readerIndex <= writerIndex(" + writerIndex + "))");
        }
        this.readerIndex = readerIndex;
        return this;
    }

    /**
     * 获取写指针位置。
     *
     * @return 当前 writerIndex
     */
    public int writerIndex() {
        return writerIndex;
    }

    /**
     * 设置写指针位置。
     *
     * @param writerIndex 新位置，范围 [readerIndex, capacity]
     * @return this
     */
    public RetainableByteBuffer writerIndex(int writerIndex) {
        if (writerIndex < readerIndex || writerIndex > buffer.capacity()) {
            throw new IndexOutOfBoundsException(
                    "writerIndex: " + writerIndex + " (expected: readerIndex(" + readerIndex + ") <= writerIndex <= capacity(" + buffer.capacity() + "))");
        }
        this.writerIndex = writerIndex;
        return this;
    }

    /**
     * 可读字节数（writerIndex - readerIndex）。
     *
     * @return 可读字节数
     */
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    /**
     * 可写入字节数（capacity - writerIndex）。
     *
     * @return 可写字节数
     */
    public int writableBytes() {
        return buffer.capacity() - writerIndex;
    }

    /**
     * 缓冲区总容量。
     *
     * @return 容量
     */
    public int capacity() {
        return buffer.capacity();
    }

    /**
     * 是否有可读数据。
     *
     * @return true 如果 readableBytes() > 0
     */
    public boolean isReadable() {
        return writerIndex > readerIndex;
    }

    /**
     * 重置读写指针到 0。
     *
     * @return this
     */
    public RetainableByteBuffer clear() {
        readerIndex = 0;
        writerIndex = 0;
        return this;
    }

    // ======================== 读操作 ========================

    /**
     * 读取一个字节（有符号），readerIndex + 1。
     *
     * @return 字节值
     */
    public byte readByte() {
        checkReadableBytes(1);
        return buffer.get(readerIndex++);
    }

    /**
     * 读取一个无符号字节，readerIndex + 1。
     *
     * @return 无符号字节值 (0~255)
     */
    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    /**
     * 读取 2 字节 short（大端序），readerIndex + 2。
     *
     * @return short 值
     */
    public short readShort() {
        checkReadableBytes(2);
        short v = buffer.getShort(readerIndex);
        readerIndex += 2;
        return v;
    }

    /**
     * 读取无符号 2 字节 short（大端序），readerIndex + 2。
     *
     * @return 无符号 short 值 (0~65535)
     */
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    /**
     * 读取 4 字节 int（大端序），readerIndex + 4。
     *
     * @return int 值
     */
    public int readInt() {
        checkReadableBytes(4);
        int v = buffer.getInt(readerIndex);
        readerIndex += 4;
        return v;
    }

    /**
     * 读取 8 字节 long（大端序），readerIndex + 8。
     *
     * @return long 值
     */
    public long readLong() {
        checkReadableBytes(8);
        long v = buffer.getLong(readerIndex);
        readerIndex += 8;
        return v;
    }

    /**
     * 读取数据到字节数组，readerIndex += bytes.length。
     *
     * @param bytes 目标数组
     * @return this
     */
    public RetainableByteBuffer readBytes(byte[] bytes) {
        return readBytes(bytes, 0, bytes.length);
    }

    /**
     * 读取数据到字节数组的指定区域。
     *
     * @param bytes  目标数组
     * @param offset 目标数组起始偏移
     * @param length 读取长度
     * @return this
     */
    public RetainableByteBuffer readBytes(byte[] bytes, int offset, int length) {
        checkReadableBytes(length);
        if (buffer.hasArray()) {
            System.arraycopy(buffer.array(), buffer.arrayOffset() + readerIndex, bytes, offset, length);
        } else {
            for (int i = 0; i < length; i++) {
                bytes[offset + i] = buffer.get(readerIndex + i);
            }
        }
        readerIndex += length;
        return this;
    }

    /**
     * 跳过指定字节数，readerIndex += length。
     *
     * @param length 跳过的字节数
     * @return this
     */
    public RetainableByteBuffer skipBytes(int length) {
        if (length > readableBytes()) {
            throw new IndexOutOfBoundsException(
                    "skipBytes: " + length + " > readableBytes: " + readableBytes());
        }
        readerIndex += length;
        return this;
    }

    // ======================== 写操作 ========================

    /**
     * 写入一个字节，writerIndex + 1。
     *
     * @param value 字节值
     * @return this
     */
    public RetainableByteBuffer writeByte(int value) {
        buffer.put(writerIndex, (byte) value);
        writerIndex += 1;
        return this;
    }

    /**
     * 写入 2 字节 short（大端序），writerIndex + 2。
     *
     * @param value short 值
     * @return this
     */
    public RetainableByteBuffer writeShort(int value) {
        buffer.putShort(writerIndex, (short) value);
        writerIndex += 2;
        return this;
    }

    /**
     * 写入 4 字节 int（大端序），writerIndex + 4。
     *
     * @param value int 值
     * @return this
     */
    public RetainableByteBuffer writeInt(int value) {
        buffer.putInt(writerIndex, value);
        writerIndex += 4;
        return this;
    }

    /**
     * 写入 8 字节 long（大端序），writerIndex + 8。
     *
     * @param value long 值
     * @return this
     */
    public RetainableByteBuffer writeLong(long value) {
        buffer.putLong(writerIndex, value);
        writerIndex += 8;
        return this;
    }

    /**
     * 写入字节数组，writerIndex += bytes.length。
     *
     * @param bytes 数据
     * @return this
     */
    public RetainableByteBuffer writeBytes(byte[] bytes) {
        return writeBytes(bytes, 0, bytes.length);
    }

    /**
     * 写入字节数组的指定区域。
     *
     * @param bytes  源数组
     * @param offset 源数组起始偏移
     * @param length 写入长度
     * @return this
     */
    public RetainableByteBuffer writeBytes(byte[] bytes, int offset, int length) {
        if (length > writableBytes()) {
            throw new IndexOutOfBoundsException(
                    "writeBytes: " + length + " > writableBytes: " + writableBytes());
        }
        if (buffer.hasArray()) {
            System.arraycopy(bytes, offset, buffer.array(), buffer.arrayOffset() + writerIndex, length);
        } else {
            for (int i = 0; i < length; i++) {
                buffer.put(writerIndex + i, bytes[offset + i]);
            }
        }
        writerIndex += length;
        return this;
    }

    /**
     * 写入另一个 RetainableByteBuffer 的可读数据。
     *
     * @param src 源缓冲区
     * @return this
     */
    public RetainableByteBuffer writeBytes(RetainableByteBuffer src) {
        int length = src.readableBytes();
        if (buffer.hasArray() && src.buffer.hasArray()) {
            System.arraycopy(src.buffer.array(), src.buffer.arrayOffset() + src.readerIndex,
                    buffer.array(), buffer.arrayOffset() + writerIndex, length);
        } else {
            for (int i = 0; i < length; i++) {
                buffer.put(writerIndex + i, src.buffer.get(src.readerIndex + i));
            }
        }
        src.readerIndex += length;
        this.writerIndex += length;
        return this;
    }

    /**
     * 写入 ByteBuffer 的剩余数据，writerIndex += src.remaining()。
     *
     * @param src 源 ByteBuffer（position 到 limit 之间的数据）
     * @return this
     */
    public RetainableByteBuffer writeBytes(ByteBuffer src) {
        int length = src.remaining();
        if (length > writableBytes()) {
            throw new IndexOutOfBoundsException(
                    "writeBytes: " + length + " > writableBytes: " + writableBytes());
        }
        if (buffer.hasArray() && src.hasArray()) {
            System.arraycopy(src.array(), src.arrayOffset() + src.position(),
                    buffer.array(), buffer.arrayOffset() + writerIndex, length);
            src.position(src.position() + length);
        } else {
            for (int i = 0; i < length; i++) {
                buffer.put(writerIndex + i, src.get());
            }
        }
        writerIndex += length;
        return this;
    }

    // ======================== 绝对位置读写（不影响指针） ========================

    /**
     * 获取指定位置的字节（不影响 readerIndex）。
     *
     * @param index 位置
     * @return 字节值
     */
    public byte getByte(int index) {
        return buffer.get(index);
    }

    /**
     * 获取指定位置的无符号字节（不影响 readerIndex）。
     *
     * @param index 位置
     * @return 无符号字节值 (0~255)
     */
    public short getUnsignedByte(int index) {
        return (short) (buffer.get(index) & 0xFF);
    }

    /**
     * 获取指定位置的 short（不影响 readerIndex）。
     *
     * @param index 位置
     * @return short 值
     */
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    /**
     * 获取指定位置的 int（不影响 readerIndex）。
     *
     * @param index 位置
     * @return int 值
     */
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    /**
     * 设置指定位置的字节（不影响 writerIndex）。
     *
     * @param index 位置
     * @param value 字节值
     * @return this
     */
    public RetainableByteBuffer setByte(int index, int value) {
        buffer.put(index, (byte) value);
        return this;
    }

    /**
     * 设置指定位置的 short（不影响 writerIndex）。
     *
     * @param index 位置
     * @param value short 值
     * @return this
     */
    public RetainableByteBuffer setShort(int index, int value) {
        buffer.putShort(index, (short) value);
        return this;
    }

    /**
     * 设置指定位置的 int（不影响 writerIndex）。
     *
     * @param index 位置
     * @param value int 值
     * @return this
     */
    public RetainableByteBuffer setInt(int index, int value) {
        buffer.putInt(index, value);
        return this;
    }

    // ======================== 零拷贝切片 ========================

    /**
     * 创建零拷贝切片，共享底层数据。
     * <p>
     * 返回一个新的 RetainableByteBuffer，其数据范围是 [readerIndex, writerIndex)，
     * 与父缓冲区共享底层 ByteBuffer 的数据。修改任一方数据对另一方可见。
     * </p>
     * <p>
     * <b>注意</b>：切片的生命周期不得超过父缓冲区。父缓冲区 release() 后，切片不可再使用。
     * 如果需要独立生命周期，应使用 {@link #copy()} 创建深拷贝。
     * </p>
     *
     * @return 共享底层数据的子缓冲区
     */
    public RetainableByteBuffer slice() {
        int len = readableBytes();
        // 设置 ByteBuffer 的 position/limit 以创建精确的 slice
        int oldPos = buffer.position();
        int oldLim = buffer.limit();
        buffer.position(readerIndex);
        buffer.limit(readerIndex + len);
        ByteBuffer sliced = buffer.slice(); // 共享底层数据，独立 position/limit/capacity
        buffer.position(oldPos);
        buffer.limit(oldLim);
        return new RetainableByteBuffer(sliced);
    }

    /**
     * 创建深拷贝，独立底层数据。
     * <p>
     * 分配新的 ByteBuffer 并复制 [readerIndex, writerIndex) 范围的数据。
     * 新缓冲区拥有完全独立的生命周期，可安全地在父缓冲区释放后继续使用。
     * </p>
     *
     * @return 数据独立的子缓冲区
     */
    public RetainableByteBuffer copy() {
        int len = readableBytes();
        ByteBuffer copy = ByteBuffer.allocate(len);
        for (int i = 0; i < len; i++) {
            copy.put(i, buffer.get(readerIndex + i));
        }
        return new RetainableByteBuffer(copy);
    }

    // ======================== 堆内存访问 ========================

    /**
     * 底层是否为堆内存 ByteBuffer（有 backing array）。
     *
     * @return true 如果是堆内存
     */
    public boolean hasArray() {
        return buffer.hasArray();
    }

    /**
     * 获取底层堆内存数组。仅当 {@link #hasArray()} 返回 true 时可用。
     *
     * @return 底层 byte[]
     */
    public byte[] array() {
        return buffer.array();
    }

    /**
     * 获取底层堆内存数组，并将 readerIndex 推进到 writerIndex（消费全部可读数据）。
     * <p>
     * 等价于 {@code array()} + {@code skipBytes(readableBytes())}，但保证原子性，
     * 避免调用方忘记推进 readerIndex。适用于需要一次性拷贝全部可读数据到目标缓冲区的场景。
     * </p>
     * <p>
     * 调用方通过 {@code arrayOffset()} 和 {@code readableBytes()}（调用前的值）
     * 确定数组中可读数据的起始偏移和长度。
     * </p>
     *
     * @return 底层 byte[]
     */
    public byte[] readArray() {
        readerIndex = writerIndex;
        return buffer.array();
    }

    /**
     * 底层数组偏移量。
     *
     * @return 数组偏移
     */
    public int arrayOffset() {
        return buffer.arrayOffset();
    }

    // ======================== I/O 兼容方法 ========================

    /**
     * 返回缓冲区中剩余的字节数（兼容旧 API）。
     *
     * @return 剩余字节数（等同于 {@link #readableBytes()}）
     */
    public int remaining() {
        return readableBytes();
    }

    /**
     * 检查缓冲区是否还有剩余数据（兼容旧 API）。
     *
     * @return true 如果 readableBytes() > 0
     */
    public boolean hasRemaining() {
        return isReadable();
    }

    /**
     * 往缓冲区追加数据（兼容旧 API）。
     * <p>
     * 使用 ByteBuffer 的 position/limit 进行写入，不影响 readerIndex/writerIndex。
     * 建议新代码使用 {@link #writeBytes(byte[])} 替代。
     * </p>
     *
     * @param bytes 要写入的字节数组
     */
    public void put(byte[] bytes) {
        BufferUtil.append(buffer, bytes);
    }

    /**
     * 从缓冲区读取数据到字节数组（兼容旧 API）。
     * <p>
     * 使用 ByteBuffer 的 position/limit 进行读取，不影响 readerIndex/writerIndex。
     * 建议新代码使用 {@link #readBytes(byte[])} 替代。
     * </p>
     *
     * @param bytes 目标字节数组
     */
    public void get(byte[] bytes) {
        BufferUtil.writeTo(buffer, bytes);
    }

    /**
     * 将缓冲区切换到填充模式（I/O 写入准备）。
     * <p>
     * 将 writerIndex 同步到 ByteBuffer.position，capacity 同步到 ByteBuffer.limit，
     * 使通道可以调用 {@code channel.read(buffer)} 追加数据。
     * I/O 完成后必须调用 {@link #flipToFlush()} 恢复。
     * </p>
     *
     * @return 切换后的 ByteBuffer，可直接传给 channel.read()
     */
    public ByteBuffer flipToFill() {
        // 同步 writerIndex 到 ByteBuffer
        buffer.position(writerIndex);
        buffer.limit(buffer.capacity());
        return buffer;
    }

    /**
     * 将缓冲区切换到刷新模式（I/O 读取完成）。
     * <p>
     * 从 ByteBuffer.position 更新 writerIndex，并重置 readerIndex 为 0，
     * 使后续 handler 可以从头读取数据。
     * </p>
     */
    public void flipToFlush() {
        // 从 I/O 同步回双指针
        writerIndex = buffer.position();
        readerIndex = 0;
        // 恢复 ByteBuffer 为读模式
        buffer.flip();
    }

    // ======================== 零拷贝视图 ========================

    /**
     * 创建可读数据的 ByteBuffer 视图，不拷贝数据。
     * <p>
     * 堆内存场景下直接包装底层数组（零拷贝），可直接传给 SSLEngine 等组件。
     * 调用方不得修改返回的 ByteBuffer 的 position/limit，且生命周期不得超过本缓冲区。
     * </p>
     *
     * @return 共享底层数据的 ByteBuffer 视图
     */
    public ByteBuffer asByteBuffer() {
        if (buffer.hasArray()) {
            // 零拷贝：直接包装底层数组的可见区域
            return ByteBuffer.wrap(buffer.array(), buffer.arrayOffset() + readerIndex, readableBytes());
        }
        // 直接内存：创建 slice 视图
        int oldPos = buffer.position();
        int oldLim = buffer.limit();
        buffer.position(readerIndex);
        buffer.limit(writerIndex);
        ByteBuffer sliced = buffer.slice();
        buffer.position(oldPos);
        buffer.limit(oldLim);
        return sliced;
    }

    // ======================== 引用计数（子类重写） ========================

    /**
     * 增加引用计数。
     * <p>
     * 基类实现为空操作。子类 {@link PooledByteBuffer} 重写此方法实现真正的引用计数递增。
     * </p>
     */
    public void retain() {
        // 基类空实现，子类重写
    }

    /**
     * 释放缓冲区。
     * <p>
     * 基类实现为空操作（返回 false）。子类 {@link PooledByteBuffer} 重写此方法，
     * 递减引用计数并在归零时归还给池。
     * </p>
     *
     * @return true 如果缓冲区已释放（引用计数归零）
     */
    public boolean release() {
        return false;
    }

    // ======================== 内部工具 ========================

    /**
     * 检查可读字节数是否满足需求。
     *
     * @param minReadableBytes 最少需要的可读字节数
     */
    private void checkReadableBytes(int minReadableBytes) {
        if (readableBytes() < minReadableBytes) {
            throw new IndexOutOfBoundsException(
                    "readableBytes: " + readableBytes() + " < expected: " + minReadableBytes);
        }
    }

    @Override
    public String toString() {
        return String.format("RetainableByteBuffer@%x{reader=%d, writer=%d, capacity=%d}",
                System.identityHashCode(this), readerIndex, writerIndex, buffer.capacity());
    }
}
