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
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FastArrayList.java
 *
 * @description:自定义高性能的线程安全集合
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class FastCopyOnWriteArrayList<T> implements Iterable<T> {


    final transient ReentrantLock lock = new ReentrantLock();

    private Class<T> type;

    /**
     * 当前下标
     */
    private int currentIndex = 0;

    /**
     * 用于存储数据,关键字transient，序列化对象的时候，这个属性就不会被序列化。
     */
    private transient volatile T[] data;


    /**
     * 定义一个常量为 0.(后面用于定义默认的集合大小)
     */
    private static final int DEFAULT_CAPACITY = 0;


    public FastCopyOnWriteArrayList(Class<T> type) {
        //实例化数组
        this.data = (T[]) Array.newInstance(type, DEFAULT_CAPACITY);
        this.type = type;
    }


    public FastCopyOnWriteArrayList() {
        //实例化数组
        this.data = (T[]) new Object[DEFAULT_CAPACITY];
    }


    /**
     * 获取数组的大小
     *
     * @return int
     */
    public int size() {
        return this.data.length;
    }

    /**
     * 根据元素获得在集合中的索引
     *
     * @param o o
     * @return int
     */
    public int indexOf(T o) {
        if (o == null) {
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < data.length; i++) {
                if (o.equals(data[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 在尾部添加元素
     *
     * @param obj obj
     * @return boolean
     */
    public boolean add(T obj) {
        return add(size(), obj);
    }

    /**
     * 添加到数组首位
     *
     * @param obj
     * @return
     */
    public boolean addFirst(T obj) {
        return add(0, obj);
    }

    /**
     * 添加最后
     *
     * @param obj
     * @return
     */
    public boolean addLast(T obj) {
        return add(size(), obj);
    }


    /**
     * 指定位置添加一个元素，如果刚好等于集合长度，在最后添加。如果在中间某个位置，则原来的位置后移一位
     * @param index
     * @param obj
     * @return
     */
    public boolean add(int index, T obj) {
        final ReentrantLock lock = this.lock;
        //加锁
        lock.lock();
        try {

            int len = arrays().length;
            T[] newData = Arrays.copyOf(data, len + 1);
            //如果给定索引长度刚好等于原数组长度，那么直接在尾部添加进去
            if (index == size()) {
                newData[len] = obj;
            } else if (checkIndexOut(index)) {
                //checkIndexOut()如果不抛异常，默认 index <=size,且 index > 0

                //原数组，原数组起始位置，目标数组，目标数组起始位置，要copy的长度
                System.arraycopy(data, 0, newData, 0, len);
                //将要插入索引位置后面的对象 拷贝。空出指定位置
                System.arraycopy(data, index, newData, index + 1, size() - index);
                newData[index] = obj;
            }
            setArray(newData);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断给定索引是否越界
     *
     * @param index index
     * @return boolean
     */
    public boolean checkIndexOut(int index) {
        if (index > size() || index < 0) {
            throw new IndexOutOfBoundsException("指定的索引越界，集合大小为:" + size() + ",您指定的索引大小为:" + index);
        }
        return true;
    }

    /**
     * 根据索引获得元素
     *
     * @param index index
     * @return T
     */
    public T get(int index) {
        checkIndexOut(index);
        return data[index];

    }

    /**
     * 获取第一个
     *
     * @return
     */
    public T getFirst() {
        return get(0);
    }

    /**
     * 获取最后一个
     *
     * @return
     */
    public T getLast() {
        return get(size() - 1);
    }

    /**
     * 删除所有元素
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            T[] newElements = (T[]) new Object[0];
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据索引删除元素
     *
     * @param index index
     * @return T
     */
    public T remove(int index) {
        final ReentrantLock lock = this.lock;
        //加锁
        lock.lock();
        try {
            T[] elements = arrays();
            int len = elements.length;
            // 先得到旧值
            T oldValue = get(index);
            int numMoved = len - index - 1;
            // 如果要删除的数据正好是数组的尾部，直接删除
            if (numMoved == 0) {
                setArray(Arrays.copyOf(elements, len - 1));
            } else {
                // 若删除的数据在数组中间：
                // 1. 设置新数组的长度减一，因为是减少一个元素
                // 2. 从 0 拷贝到数组新位置
                // 3. 从新位置拷贝到数组尾部
                T[] newElements = (T[]) new Object[len - 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index, numMoved);
                setArray(newElements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除指定的元素，删除成功返回 true，失败返回 false
     *
     * @param obj obj
     * @return boolean
     */
    public boolean remove(T obj) {
        for (int i = 0; i < size(); i++) {
            if (obj.equals(data[i])) {
                remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 在指定位置修改元素，通过索引，修改完成后返回原数据
     *
     * @param index index
     * @param obj   obj
     * @return T
     */
    public T set(int index, T obj) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            checkIndexOut(index);
            T[] elements = arrays();
            T oldValue = get(index);

            if (oldValue != obj) {
                int len = elements.length;
                T[] newElements = Arrays.copyOf(elements, len);
                newElements[index] = obj;
                setArray(newElements);
            } else {
                // Not quite a no-op; ensures volatile write semantics
                setArray(elements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查看集合中是否包含某个元素，如果有，返回 true，没有返回 false
     *
     * @param obj obj
     * @return boolean
     */
    public boolean contain(T obj) {
        for (int i = 0; i < data.length; i++) {
            if (obj.equals(data[i])) {
                return true;
            }
        }
        return false;
    }

    public T[] arrays() {
        return data;
    }

    public void setArray(T[] arr) {
        this.data = arr;
    }


    public T[] toArray() {
        T[] elements = arrays();
        return Arrays.copyOf(elements, elements.length);
    }


    /**
     * 轮训，均衡的随机获取数组里面的元素
     *
     * @return
     */
    public T round() {
        currentIndex = (currentIndex + 1) % size();
        return this.get(currentIndex);
    }


    @Override
    public Iterator<T> iterator() {
        class iter implements Iterator<T> {
            @Override
            public boolean hasNext() {
                return (currentIndex < size());
            }

            @Override
            public T next() {
                return data[currentIndex++];
            }

            @Override
            public void remove() {

            }
        }

        return new iter();

    }
}
