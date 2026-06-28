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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于链表 + CAS 的无锁 MPSC（Multiple Producers, Single Consumer）队列。
 * <p>
 * 专为"多线程入队、单线程出队"场景设计，典型用途：跨线程对象回收。
 * </p>
 *
 * <h3>算法说明（Michael-Scott Queue）：</h3>
 * <ul>
 *   <li><b>入队（多生产者）</b>：通过 CAS 将新节点链接到当前尾节点的 {@code next}，
 *       再尝试推进 {@code tail} 指针。多个生产者并发时，CAS 失败者自旋重试，无需加锁。</li>
 *   <li><b>出队（单消费者）</b>：直接从 {@code head.next} 取值并推进 {@code head}，
 *       由于只有一个消费者，无需任何同步操作。</li>
 *   <li><b>哨兵节点</b>：初始时 {@code head == tail}（空哨兵），避免空队列的边界判断。</li>
 * </ul>
 *
 * <h3>性能特征：</h3>
 * <ul>
 *   <li>{@link #offer(Object)}：约 10-20ns（一次 CAS 成功 + 一次 CAS 尝试推进 tail）</li>
 *   <li>{@link #poll()}：约 2-5ns（直接读 volatile，无 CAS）</li>
 *   <li>{@link #drainTo(Consumer)}：批量消费，摊薄每次 poll 的开销</li>
 * </ul>
 *
 * <h3>线程安全约束：</h3>
 * <ul>
 *   <li>{@link #offer(Object)} 可从任意线程并发调用</li>
 *   <li>{@link #poll()} 和 {@link #drainTo(Consumer)} 必须由同一线程（消费者）调用</li>
 * </ul>
 *
 * @param <E> 元素类型
 * @author Getty Project
 */
public class MpscLinkedQueue<E> {

    /**
     * 链表节点。
     * <p>
     * {@code next} 使用 {@link AtomicReference}，支持 CAS 链接操作。
     * </p>
     */
    private static class Node<E> {
        final E item;
        final AtomicReference<Node<E>> next = new AtomicReference<>(null);

        Node(E item) {
            this.item = item;
        }
    }

    /**
     * 哨兵头节点。出队时推进，始终指向"上一次出队的节点"（其 next 指向当前真正的队头）。
     * <p>
     * volatile 保证消费者写入后对生产者可见（生产者遍历 next 链时需要读到最新的 head）。
     * </p>
     */
    private volatile Node<E> head;

    /**
     * 尾节点。入队时通过 CAS 推进。
     * <p>
     * 可能滞后于实际链表末尾（生产者完成 CAS1 但尚未完成 CAS2 时），
     * 但不影响正确性：后续生产者会通过 next 链遍历到真正的末尾。
     * </p>
     */
    private volatile Node<E> tail;

    /**
     * 构造空队列（创建哨兵节点）。
     */
    public MpscLinkedQueue() {
        Node<E> sentinel = new Node<>(null);
        this.head = sentinel;
        this.tail = sentinel;
    }

    // ======================== 入队（多生产者，CAS 无锁） ========================

    /**
     * 将元素追加到队列尾部。
     * <p>
     * 无锁实现，多个线程可同时调用，通过 CAS 竞争完成链接。
     * 无界队列，始终返回 true。
     * </p>
     *
     * @param e 待入队的元素（不允许 null）
     * @return true（始终成功）
     * @throws NullPointerException 如果 e 为 null
     */
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException("item must not be null");
        }
        Node<E> newNode = new Node<>(e);
        Node<E> t = tail;
        // CAS 循环：将新节点链接到当前 tail 的 next
        while (!t.next.compareAndSet(null, newNode)) {
            // 其他生产者已抢先，沿 next 链前进后重试
            Node<E> next = t.next.get();
            if (next != null) {
                t = next;
            }
        }
        // 尝试推进 tail（失败无影响，其他线程或消费者会帮忙推进）
        tail = newNode;
        return true;
    }

    // ======================== 出队（单消费者，无 CAS） ========================

    /**
     * 从队列头部取出一个元素。
     * <p>
     * 必须由消费者线程调用。无 CAS，直接读取 volatile 节点。
     * </p>
     *
     * @return 队头元素，队列为空时返回 null
     */
    public E poll() {
        Node<E> h = head;
        Node<E> first = h.next.get();
        if (first == null) {
            return null; // 队列为空
        }
        // 推进 head 到 first（first 成为新的哨兵节点）
        head = first;
        return first.item;
    }

    /**
     * 批量消费队列中所有可用元素。
     * <p>
     * 将队列中所有元素依次传递给 {@code consumer}，然后清空队列。
     * 比逐个 {@link #poll()} 更高效，适合在 allocate 前一次性回收所有跨线程归还的缓冲区。
     * </p>
     * <p>
     * 必须由消费者线程调用。
     * </p>
     *
     * @param consumer 消费函数，接收每个出队元素
     * @return 本次消费的元素数量
     */
    public int drainTo(Consumer<E> consumer) {
        int count = 0;
        E item;
        while ((item = poll()) != null) {
            consumer.accept(item);
            count++;
        }
        return count;
    }

    /**
     * 队列是否为空（近似判断）。
     * <p>
     * 由于生产者可并发入队，返回 true 后可能有新元素入队。
     * 仅供消费者线程使用，用于判断是否需要 drain。
     * </p>
     *
     * @return true 表示当前（瞬时）队列为空
     */
    public boolean isEmpty() {
        return head.next.get() == null;
    }

    @Override
    public String toString() {
        return "MpscLinkedQueue{head=" + System.identityHashCode(head)
                + ",tail=" + System.identityHashCode(tail) + "}";
    }
}
