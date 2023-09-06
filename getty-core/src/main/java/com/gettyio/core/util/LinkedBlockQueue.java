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
package com.gettyio.core.util;

import java.lang.reflect.Array;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LinkedBlockQueue.java
 *
 * 自定义读写mq,元素满时阻塞
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class LinkedBlockQueue<T> implements LinkedQueue<T> {

    /**
     * 队列实现
     */
    T[] items;

    /**
     * 初始大小
     */
    int capacity;

    /**
     * 元素个数
     */
    private int count;

    /**
     * 准备插入位置
     */
    private int putIndex = 0;
    /**
     * 准备移除位置
     */
    private int removeIndex = 0;

    ReentrantLock lock = new ReentrantLock(true);
    /**
     * 队列满情况
     */
    Condition notFull = lock.newCondition();
    /**
     * 队列为空情况
     */
    Condition notEmpty = lock.newCondition();


    public LinkedBlockQueue() {
        this(1024);
    }

    public LinkedBlockQueue(int capacity) {
        items = (T[]) new Object[capacity];
        this.capacity = capacity;
    }

    @Override
    public <T> T[] getArray(Class<T> componentType, int length) {
        return (T[]) Array.newInstance(componentType, length);
    }


    /**
     * 进队 插入最后一个元素位置
     *
     * @param t 泛型
     * @throws InterruptedException 异常
     */
    @Override
    public T put(T t) throws InterruptedException {
        //检查是否为空
        checkNull(t);
        //获取锁
        lock.lock();
        try {
            //已经满了 则发生阻塞 无法继续插入
            while (items.length == count) {
                notFull.await();
            }
            items[putIndex] = t;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        return t;
    }

    /**
     * 出队 最后一个元素
     *
     * @return T
     * @throws InterruptedException 可能抛出异常
     */
    @Override
    public T poll() throws InterruptedException {

        lock.lock();
        try {
            // 如果队列没有元素则返回null，否则出队
            return (count == 0) ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T take() throws InterruptedException {

        lock.lock();
        try {
            while (count == 0) {
                //出队阻塞
                notEmpty.await();
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    private T dequeue() {
        T t = this.items[removeIndex];
        this.items[removeIndex] = null;
        if (++removeIndex == items.length) {
            removeIndex = 0;
        }
        count--;
        notFull.signal();
        return t;
    }


    private void checkNull(T t) {
        if (t == null) {
            throw new NullPointerException();
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
