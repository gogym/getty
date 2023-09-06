/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.util;

import java.lang.reflect.Array;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * LinkedNonBlockQueue.java
 *
 * @description:数组实现的出队非阻塞队列
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class LinkedNonReadBlockQueue<T> implements LinkedQueue<T> {
    /**
     * 队列实现
     */
    T[] items;

    /**
     * 初始大小
     */
    int capacity = 1024;

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

    boolean isFull = false;


    public LinkedNonReadBlockQueue() {
        this(1024);
    }

    public LinkedNonReadBlockQueue(int capacity) {
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
                isFull = true;
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
     * 出队 最后一个元素
     *
     * @return T
     * @throws InterruptedException 可能抛出异常
     */
    @Override
    public T poll() throws InterruptedException {
        if (count == 0) {
            if (isFull) {
                lock.lock();
                try {
                    isFull = false;
                    notFull.signal();
                } finally {
                    lock.unlock();
                }
            }
            //如果队列中没有元素，直接返回null。不阻塞
            return null;
        }

        T t = this.items[removeIndex];
        this.items[removeIndex] = null;
        if (++removeIndex == items.length) {
            removeIndex = 0;
        }
        count--;

        return t;
    }

    @Override
    public T take() throws InterruptedException {
        return poll();
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
