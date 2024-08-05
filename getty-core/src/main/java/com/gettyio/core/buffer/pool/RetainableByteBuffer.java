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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


/**
 * 实现Retainable接口的ByteBuffer类，用于管理可以保留的字节缓冲区。
 * 该类的实例可以在需要时保留字节缓冲区的内容，以确保数据不会被过早地释放或覆盖。
 */
public class RetainableByteBuffer implements Retainable {

    /**
     * 用于存储数据的ByteBuffer
     * 使用ByteBuffer作为缓冲区，以提高数据处理的效率。
     * 由于ByteBuffer是final类型的，因此确保了线程安全性和数据一致性。
     */
    private final ByteBuffer buffer;

    /**
     * 使用AtomicInteger来管理当前实例的引用计数。
     * 这个计数器用于实现软引用的垃圾收集机制，当引用计数为0时，表示当前实例不再被需要，
     * 可以被垃圾收集器回收，从而释放内存资源。
     * 使用AtomicInteger保证了引用计数的线程安全，可以在多线程环境下正确地增加或减少计数。
     */
    private final AtomicInteger references = new AtomicInteger();

    /**
     * 当引用计数归零时，用于执行释放逻辑的Consumer接口实例
     */
    private final Consumer<RetainableByteBuffer> releaser;

    /**
     * 用于记录上一次更新操作的时间戳。
     * 使用AtomicLong确保在并发环境下的安全性，允许高效的原子操作更新。
     * 这个字段被声明为final，意味着它的引用不会改变，确保了线程安全。
     */
    private final AtomicLong lastUpdate = new AtomicLong(System.nanoTime());


    /**
     * 构造函数，初始化RetainableByteBuffer实例。
     *
     * @param buffer   ByteBuffer对象，用于数据存储。
     * @param releaser 释放处理器，当这个对象的引用计数为0时，会调用此处理器进行资源释放。
     */
    RetainableByteBuffer(ByteBuffer buffer, Consumer<RetainableByteBuffer> releaser) {
        this.releaser = releaser;
        this.buffer = buffer;
    }

    /**
     * 获取缓冲区的容量。
     * <p>
     * 容量是缓冲区可以存储的最大元素数量。这个值是固定的，
     * 一旦缓冲区被创建，就不能更改其容量。
     *
     * @return 缓冲区的容量。
     */
    public int capacity() {
        return buffer.capacity();
    }


    /**
     * 获取底层的ByteBuffer对象。
     *
     * @return ByteBuffer对象。
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * 获取最后一次更新的时间戳。
     *
     * @return 最后一次更新的时间戳。
     */
    public long getLastUpdate() {
        return lastUpdate.get();
    }

    /**
     * 检查此缓冲区是否被保留。当retain()方法至少比release()方法多调用一次时，返回true。
     *
     * @return 如果此缓冲区被保留，则返回true；否则返回false。
     */
    public boolean isRetained() {
        return references.get() > 1;
    }

    /**
     * 检查底层的ByteBuffer是否为直接缓冲区。
     *
     * @return 如果是直接缓冲区，则返回true；否则返回false。
     */
    public boolean isDirect() {
        return buffer.isDirect();
    }

    /**
     * 增加此缓冲区的保留计数器。这必须在池创建后内部进行，以及每次解池后进行。
     * 此方法存在的原因是能够在必须知道引用计数器为何增加的情况下进行一些安全性检查。
     * 此方法不接受参数，也不返回任何值。
     *
     * @throws IllegalStateException 如果在仍被使用时重新放入池中，则抛出此异常。
     */
    void acquire() {
        // 尝试增加引用计数器，如果当前计数器为0，则设置为1；如果计数器不为0，表示对象仍在使用中，抛出异常。
        if (references.getAndUpdate(c -> c == 0 ? 1 : c) != 0) {
            throw new IllegalStateException("re-pooled while still used " + this);
        }
    }


    /**
     * 增加此缓冲区的保留计数器。当对象被保留时，此方法被调用以增加保留计数，确保对象不会被过早释放。
     *
     * @throws IllegalStateException 如果在尝试保留时对象已被释放，则抛出此异常。
     */
    @Override
    public void retain() {
        // 尝试增加保留计数器，如果当前计数器为0（即对象已被释放），则抛出异常。
        if (references.getAndUpdate(c -> c == 0 ? 0 : c + 1) == 0) {
            throw new IllegalStateException("released " + this);
        }
    }


    /**
     * 递减此缓冲区的保留计数器。
     *
     * @return 如果缓冲区被重新池化，则返回true；否则返回false。
     */
    public boolean release() {
        // 尝试递减引用计数，如果计数为0，则抛出异常；否则返回递减后的计数。
        int ref = references.updateAndGet(c ->
        {
            if (c == 0) {
                throw new IllegalStateException("already released " + this);
            }
            return c - 1;
        });
        // 如果引用计数递减至0，则进行后续的释放操作，并返回true。
        if (ref == 0) {
            // 更新最后修改时间
            lastUpdate.set(System.nanoTime());
            // 执行释放操作
            releaser.accept(this);
            return true;
        }
        return false;
    }

    /**
     * 返回缓冲区中剩余的字节数。
     *
     * @return 剩余字节数。
     */
    public int remaining() {
        return buffer.remaining();
    }

    /**
     * 检查缓冲区是否还有剩余数据。
     *
     * @return 如果还有数据未处理，则返回true；否则返回false。
     */
    public boolean hasRemaining() {
        return remaining() > 0;
    }

    /**
     * 检查缓冲区是否为空。
     *
     * @return 如果缓冲区为空，则返回true；否则返回false。
     */
    public boolean isEmpty() {
        return !hasRemaining();
    }

    /**
     * 清除缓冲区的数据。
     */
    public void clear() {
        BufferUtil.clear(buffer);
    }


    /**
     * 往缓冲区追加数据
     *
     * @param bytes
     */
    public void put(byte[] bytes) {
        BufferUtil.append(buffer, bytes);
    }


    /**
     * 将字节数组写入缓冲区。
     *
     * @param bytes 待写入的字节数组。
     * @throws IOException 如果在写入过程中发生I/O错误。
     */
    public void get(byte[] bytes) throws IOException {
        // 将字节数组写入缓冲区
        BufferUtil.writeTo(buffer, bytes);
    }


    /**
     * 将缓冲区切换到填充模式
     *
     * @return
     */
    public ByteBuffer flipToFill() {
        BufferUtil.flipToFill(buffer);
        return buffer;
    }


    /**
     * 将缓冲区切换到刷新模式。
     */
    public void flipToFlush() {
        BufferUtil.flipToFlush(buffer);
    }


    /**
     * 生成并返回当前缓冲区对象的字符串表示形式。
     *
     * @return 表示当前缓冲区状态的字符串。
     */
    @Override
    public String toString() {
        return String.format("%s@%x{%s,r=%d}", getClass().getSimpleName(), hashCode(), BufferUtil.toDetailString(buffer), references.get());
    }

}
