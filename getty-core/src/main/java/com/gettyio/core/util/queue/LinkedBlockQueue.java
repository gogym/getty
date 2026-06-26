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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import java.lang.reflect.Array;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于数组环形缓冲区实现的阻塞队列。
 * <p>
 * 入队（put）时队列满则阻塞，出队（take）时队列空则阻塞。
 * 使用非公平锁以获得更高的吞吐量。
 * </p>
 *
 * @param <T> 元素类型
 * @author gogym
 * @date 2020/4/9
 */
public class LinkedBlockQueue<T> implements LinkedQueue<T> {

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

    /** 队列空时的等待条件 */
    private final Condition notEmpty = lock.newCondition();

    /** 标记队列是否已释放（用于 close 场景拒绝新的入队） */
    private boolean released;

    /**
     * 默认容量 1024
     */
    public LinkedBlockQueue() {
        this(1024);
    }

    /**
     * 指定容量构造
     *
     * @param capacity 队列容量
     */
    public LinkedBlockQueue(int capacity) {
        this.items = (T[]) new Object[capacity];
        this.capacity = capacity;
    }

    @Override
    public <T> T[] getArray(Class<T> componentType, int length) {
        return (T[]) Array.newInstance(componentType, length);
    }

    /**
     * 入队操作。将元素加入队列尾部，队列满时阻塞。
     * <p>
     * 若队列已被 drain 释放，则立即抛出 InterruptedException。
     * </p>
     *
     * @param t 要入队的元素（不允许 null）
     * @return 入队的元素
     * @throws InterruptedException 等待时被中断或队列已释放
     */
    @Override
    public T put(T t) throws InterruptedException {
        if (t == null) {
            throw new NullPointerException("Element must not be null");
        }
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (released) {
                    throw new InterruptedException("Queue has been drained/closed");
                }
                notFull.await();
            }
            if (released) {
                throw new InterruptedException("Queue has been drained/closed");
            }
            enqueue(t);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        return t;
    }

    /**
     * 非阻塞出队操作。队列为空时返回 {@code null}。
     * <p>
     * 使用 tryLock() 快速路径：无竞争时直接获取锁，避免 CAS 自旋开销。
     * 获取锁失败时立即返回 null（不阻塞）。
     * </p>
     *
     * @return 队头元素，队列为空或有竞争时返回 {@code null}
     * @throws InterruptedException 操作时被中断
     */
    @Override
    public T poll() throws InterruptedException {
        if (!lock.tryLock()) {
            return null;
        }
        try {
            return (count == 0) ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 阻塞出队操作。队列为空时阻塞等待。
     *
     * @return 队头元素
     * @throws InterruptedException 等待时被中断
     */
    @Override
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 内部入队操作（必须在持有锁时调用）
     */
    private void enqueue(T t) {
        items[putIndex] = t;
        if (++putIndex == items.length) {
            putIndex = 0;
        }
        count++;
    }

    /**
     * 内部出队操作（必须在持有锁时调用）
     */
    private T dequeue() {
        T t = items[removeIndex];
        items[removeIndex] = null; // 帮助 GC
        if (++removeIndex == items.length) {
            removeIndex = 0;
        }
        count--;
        notFull.signal();
        return t;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getCount() {
        return count;
    }

    /**
     * 排空队列中的所有元素并唤醒所有等待的线程。
     * <p>
     * 设置 released 标记使后续 put 操作立即失败。
     * 如果队列元素类型为 {@link PooledByteBuffer}，则自动调用 release() 释放资源。
     * 同时通过 signalAll 唤醒因 put/take 而阻塞的线程。
     * </p>
     */
    @Override
    public void drain() {
        lock.lock();
        try {
            released = true;
            for (int i = 0; i < count; i++) {
                T item = items[removeIndex];
                items[removeIndex] = null;
                if (item instanceof PooledByteBuffer) {
                    ((PooledByteBuffer) item).release();
                }
                if (++removeIndex == items.length) {
                    removeIndex = 0;
                }
            }
            count = 0;
            putIndex = 0;
            removeIndex = 0;
            notFull.signalAll();
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
