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
package com.gettyio.core.util.queue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 基于环形数组的无锁 MPSC（多生产者、单消费者）回收队列。
 * <p>
 * 专为 {@code PooledByteBuffer} 跨线程回收设计，使用并行数组存储 4 个字段
 * （ByteBuffer、PoolChunk、offset、normCapacity），完全消除入队时的对象分配
 * （无 CacheEntry、无 Node 节点）。
 * </p>
 *
 * <h3>与 MpscLinkedQueue 的对比：</h3>
 * <table>
 *   <tr><th></th><th>MpscLinkedQueue</th><th>MpscRecycleQueue</th></tr>
 *   <tr><td>每次 offer 分配</td><td>1 Node + AtomicReference</td><td>0 对象</td></tr>
 *   <tr><td>内存布局</td><td>链表节点分散</td><td>连续数组，CPU 缓存友好</td></tr>
 *   <tr><td>容量</td><td>无界</td><td>有界（默认 4096）</td></tr>
 *   <tr><td>满时行为</td><td>永不失败</td><td>返回 false，调用方降级处理</td></tr>
 * </table>
 *
 * <h3>内存可见性保证：</h3>
 * <p>
 * 生产者写入 buffer/chunk/offset/normCapacity 后，通过
 * {@code AtomicReferenceArray.set()}（volatile write）发布 READY 状态；
 * 消费者通过 {@code AtomicReferenceArray.get()}（volatile read）读取 READY 后
 * 再访问其他字段，Java 内存模型保证可见性。
 * </p>
 *
 * @author Getty Project
 */
public final class MpscRecycleQueue {

    // 槽位状态常量
    private static final int EMPTY = 0;
    private static final int READY = 1;

    /**
     * 默认容量：4096 个槽位。
     * 足以应对突发写入，同时内存占用很小（4096 × ~32B ≈ 128KB）。
     */
    private static final int DEFAULT_CAPACITY = 4096;

    /** 环形缓冲区容量（2 的幂） */
    private final int capacity;

    /** 位掩码，用于快速取模：index & mask */
    private final int mask;

    /** 槽位状态数组（使用 AtomicIntegerArray 保证 volatile 语义） */
    private final AtomicIntegerArray states;

    /** ByteBuffer 引用数组 */
    private final AtomicReferenceArray<ByteBuffer> buffers;

    /** PoolChunk 引用数组 */
    private final AtomicReferenceArray<Object> chunks;

    /** Chunk 偏移量数组（原始 int 数组，由 states 的 volatile 保证可见性） */
    private final int[] offsets;

    /** 规范化容量数组 */
    private final int[] normCapacities;

    /** 生产者写指针（多生产者通过 CAS 竞争） */
    private final AtomicInteger producerIndex = new AtomicInteger(0);

    /** 消费者读指针（仅消费者线程访问，无需原子） */
    private int consumerIndex = 0;

    /**
     * 使用默认容量（4096）构造队列。
     */
    public MpscRecycleQueue() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 构造指定容量的队列。
     *
     * @param requestedCapacity 请求容量，会自动向上取整到 2 的幂
     */
    public MpscRecycleQueue(int requestedCapacity) {
        int cap = Integer.highestOneBit(Math.max(requestedCapacity, 16));
        if (cap < requestedCapacity) {
            cap <<= 1;
        }
        this.capacity = cap;
        this.mask = cap - 1;
        this.states = new AtomicIntegerArray(cap);
        this.buffers = new AtomicReferenceArray<>(cap);
        this.chunks = new AtomicReferenceArray<>(cap);
        this.offsets = new int[cap];
        this.normCapacities = new int[cap];
    }

    // ======================== 入队（多生产者，CAS 无锁） ========================

    /**
     * 将回收信息入队。零对象分配。
     * <p>
     * 多生产者通过 CAS 竞争写指针获取槽位，然后写入 4 个字段并发布 READY 状态。
     * 如果队列满，返回 false，调用方应降级为直接归还给 Arena。
     * </p>
     *
     * @param buffer       回收的 ByteBuffer
     * @param chunk        所属 PoolChunk（可为 null）
     * @param offset       Chunk 内偏移量
     * @param normCapacity 规范化容量
     * @return true 入队成功，false 队列满
     */
    public boolean offer(ByteBuffer buffer, Object chunk, int offset, int normCapacity) {
        int idx;
        // CAS 循环：竞争获取一个槽位索引
        while (true) {
            idx = producerIndex.get();
            int slotIdx = idx & mask;
            if (states.get(slotIdx) != EMPTY) {
                // 队列满：此槽位尚未被消费者清空
                return false;
            }
            if (producerIndex.compareAndSet(idx, idx + 1)) {
                break;
            }
        }

        int slot = idx & mask;
        // 写入 4 个字段（非 volatile，由后续的 volatile write 保证可见性）
        buffers.set(slot, buffer);
        chunks.set(slot, chunk);
        offsets[slot] = offset;
        normCapacities[slot] = normCapacity;
        // volatile write：发布 READY 状态，消费者可安全读取其他字段
        states.set(slot, READY);
        return true;
    }

    // ======================== 出队（单消费者，无 CAS） ========================

    /**
     * 批量消费队列中所有可用元素。
     * <p>
     * 将每个元素的 4 个字段通过 {@code consumer} 回调传递，
     * 消费完毕后标记槽位为 EMPTY，允许生产者复用。
     * </p>
     * <p>
     * 必须由消费者（owner）线程调用。
     * </p>
     *
     * @param consumer 消费回调，接收 (buffer, chunk, offset, normCapacity)
     * @return 本次消费的元素数量
     */
    public int drainTo(RecycleConsumer consumer) {
        int count = 0;
        while (true) {
            int slot = consumerIndex & mask;
            if (states.get(slot) != READY) {
                break;
            }
            // volatile read 已保证其他字段可见
            ByteBuffer buffer = buffers.get(slot);
            Object chunk = chunks.get(slot);
            int offset = offsets[slot];
            int normCap = normCapacities[slot];

            // 立即标记 EMPTY，允许生产者复用此槽位
            states.set(slot, EMPTY);
            consumerIndex++;
            count++;

            consumer.accept(buffer, chunk, offset, normCap);
        }
        return count;
    }

    /**
     * 队列是否为空（近似判断）。
     * <p>
     * 仅供消费者线程使用。
     * </p>
     *
     * @return true 表示当前（瞬时）队列为空
     */
    public boolean isEmpty() {
        return states.get(consumerIndex & mask) != READY;
    }

    /**
     * 回收消费回调接口。
     * <p>
     * 直接传递 4 个原始字段，避免创建包装对象。
     * </p>
     */
    @FunctionalInterface
    public interface RecycleConsumer {
        void accept(ByteBuffer buffer, Object chunk, int offset, int normCapacity);
    }

    @Override
    public String toString() {
        int produced = producerIndex.get();
        return "MpscRecycleQueue{capacity=" + capacity
                + ",produced=" + produced
                + ",consumed=" + consumerIndex + "}";
    }
}
