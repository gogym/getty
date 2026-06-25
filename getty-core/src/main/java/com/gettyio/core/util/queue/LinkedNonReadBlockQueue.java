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

import java.lang.reflect.Array;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于数组环形缓冲区实现的读非阻塞队列。
 * <p>
 * 入队（put）时队列满则阻塞，出队（poll）时队列空则立即返回 null（不阻塞）。
 * 适用于生产端需要背压、消费端需要快速返回的场景。
 * 使用非公平锁以获得更高的吞吐量。
 * </p>
 *
 * @param <T> 元素类型
 * @author gogym
 * @date 2020/4/9
 */
public class LinkedNonReadBlockQueue<T> implements LinkedQueue<T> {

    /** 环形缓冲区数组 */
    private final T[] items;

    /** 队列容量 */
    private final int capacity;

    /** 当前元素数量 */
    private int count;

    /** 下一个入队位置 */
    private int putIndex;

    /** 下一个出队位置 */
    private int removeIndex;

    /** 非公平锁（比公平锁性能更好） */
    private final ReentrantLock lock = new ReentrantLock(false);

    /** 队列满时的等待条件 */
    private final Condition notFull = lock.newCondition();

    /** 标记队列是否曾经满过（用于 poll 时唤醒等待的生产者） */
    private boolean wasFull;

    /**
     * 默认容量 1024
     */
    public LinkedNonReadBlockQueue() {
        this(1024);
    }

    /**
     * 指定容量构造
     *
     * @param capacity 队列容量
     */
    public LinkedNonReadBlockQueue(int capacity) {
        this.items = (T[]) new Object[capacity];
        this.capacity = capacity;
    }

    @Override
    public <T> T[] getArray(Class<T> componentType, int length) {
        return (T[]) Array.newInstance(componentType, length);
    }

    /**
     * 入队操作。将元素加入队列尾部，队列满时阻塞。
     *
     * @param t 要入队的元素（不允许 null）
     * @return 入队的元素
     * @throws InterruptedException 等待时被中断
     */
    @Override
    public T put(T t) throws InterruptedException {
        checkNull(t);
        lock.lock();
        try {
            while (count == items.length) {
                wasFull = true;
                notFull.await();
            }
            items[putIndex] = t;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;
        } finally {
            lock.unlock();
        }
        return t;
    }

    /**
     * 非阻塞出队操作。队列为空时返回 {@code null}。
     * <p>
     * 如果队列曾经满过，则在取出元素后唤醒等待的生产者线程。
     * </p>
     *
     * @return 队头元素，队列为空时返回 {@code null}
     * @throws InterruptedException 操作时被中断
     */
    @Override
    public T poll() throws InterruptedException {
        if (count == 0) {
            // 队列为空时检查是否需要唤醒生产者
            if (wasFull) {
                lock.lock();
                try {
                    wasFull = false;
                    notFull.signal();
                } finally {
                    lock.unlock();
                }
            }
            return null;
        }

        T t = items[removeIndex];
        items[removeIndex] = null; // 帮助 GC
        if (++removeIndex == items.length) {
            removeIndex = 0;
        }
        count--;

        // 如果之前满了，现在有空间了，在锁保护下唤醒生产者
        if (wasFull) {
            lock.lock();
            try {
                wasFull = false;
                notFull.signal();
            } finally {
                lock.unlock();
            }
        }

        return t;
    }

    /**
     * 在此实现中等价于 {@link #poll()}（读非阻塞语义）
     *
     * @return 队头元素，队列为空时返回 {@code null}
     * @throws InterruptedException 操作时被中断
     */
    @Override
    public T take() throws InterruptedException {
        return poll();
    }

    /**
     * 检查元素不为 null
     */
    private void checkNull(T t) {
        if (t == null) {
            throw new NullPointerException("Element must not be null");
        }
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getCount() {
        return count;
    }
}
