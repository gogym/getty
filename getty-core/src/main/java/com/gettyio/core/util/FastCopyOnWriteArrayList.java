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
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 线程安全的高性能数组列表，基于 Copy-on-Write 机制。
 * <p>
 * 读操作无锁，写操作通过 {@link ReentrantLock} 保护并创建新数组副本。
 * 适用于读多写少的并发场景。
 * </p>
 *
 * @param <T> 元素类型
 * @author gogym
 * @date 2020/4/9
 */
public class FastCopyOnWriteArrayList<T> implements Iterable<T> {

    /** 默认初始容量 */
    private static final int DEFAULT_CAPACITY = 8;

    /** 写操作锁 */
    private final transient ReentrantLock lock = new ReentrantLock();

    /** 底层数据存储（volatile 保证可见性） */
    private transient volatile T[] data;

    /** 实际元素数量 */
    private volatile int size;

    /** 轮询索引 */
    private int roundIndex = 0;

    // ===================== 构造函数 =====================

    /**
     * 指定元素类型构造
     *
     * @param type 元素 Class
     */
    public FastCopyOnWriteArrayList(Class<T> type) {
        this.data = (T[]) Array.newInstance(type, DEFAULT_CAPACITY);
        this.size = 0;
    }

    /**
     * 默认构造（使用 Object[] 底层数组）
     */
    public FastCopyOnWriteArrayList() {
        this.data = (T[]) new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }

    // ===================== 基础信息 =====================

    /**
     * 返回当前元素数量
     *
     * @return 元素数量
     */
    public int size() {
        return size;
    }

    /**
     * 判断列表是否为空
     *
     * @return {@code true} 如果列表不含任何元素
     */
    public boolean isEmpty() {
        return size == 0;
    }

    // ===================== 查找 =====================

    /**
     * 查找元素在列表中的索引
     *
     * @param o 目标元素
     * @return 索引，未找到返回 -1
     */
    public int indexOf(T o) {
        T[] snapshot = data;
        int len = size;
        if (o == null) {
            for (int i = 0; i < len; i++) {
                if (snapshot[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < len; i++) {
                if (o.equals(snapshot[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 判断列表是否包含指定元素
     *
     * @param obj 目标元素
     * @return {@code true} 如果包含该元素
     */
    public boolean contains(T obj) {
        return indexOf(obj) >= 0;
    }

    // ===================== 添加 =====================

    /**
     * 在尾部添加元素
     *
     * @param obj 要添加的元素
     * @return 始终返回 {@code true}
     */
    public boolean add(T obj) {
        return add(size, obj);
    }

    /**
     * 在首部添加元素
     *
     * @param obj 要添加的元素
     * @return 始终返回 {@code true}
     */
    public boolean addFirst(T obj) {
        return add(0, obj);
    }

    /**
     * 在尾部添加元素（等价于 {@link #add(Object)}）
     *
     * @param obj 要添加的元素
     * @return 始终返回 {@code true}
     */
    public boolean addLast(T obj) {
        return add(size, obj);
    }

    /**
     * 在指定位置插入元素。
     * <p>
     * 写操作在锁保护下原子执行：创建新数组，拷贝元素，插入新值。
     * </p>
     *
     * @param index 插入位置
     * @param obj   要插入的元素
     * @return 始终返回 {@code true}
     */
    public boolean add(int index, T obj) {
        lock.lock();
        try {
            int len = size;
            if (index < 0 || index > len) {
                throw new IndexOutOfBoundsException(
                        "Index: " + index + ", Size: " + len);
            }

            // 确保容量足够（至少 +1，扩容时翻倍）
            T[] oldData = data;
            int oldCapacity = oldData.length;
            int newLen = len + 1;

            T[] newData;
            if (newLen > oldCapacity) {
                // 扩容：取双倍或所需大小中的较大值
                int newCapacity = Math.max(oldCapacity * 2, newLen);
                newData = (T[]) new Object[newCapacity];
            } else {
                newData = (T[]) new Object[oldCapacity];
            }

            // 拷贝插入位置之前的元素
            if (index > 0) {
                System.arraycopy(oldData, 0, newData, 0, index);
            }
            // 插入新元素
            newData[index] = obj;
            // 拷贝插入位置之后的元素
            int tailLen = len - index;
            if (tailLen > 0) {
                System.arraycopy(oldData, index, newData, index + 1, tailLen);
            }

            data = newData;
            size = newLen;
            return true;
        } finally {
            lock.unlock();
        }
    }

    // ===================== 索引校验 =====================

    /**
     * 校验索引是否越界
     *
     * @param index 待校验的索引
     * @return 始终返回 {@code true}
     * @throws IndexOutOfBoundsException 如果索引越界
     */
    public boolean checkIndexOut(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Size: " + size);
        }
        return true;
    }

    // ===================== 获取 =====================

    /**
     * 获取指定位置的元素（无锁读）
     *
     * @param index 索引
     * @return 对应元素
     */
    public T get(int index) {
        checkIndexOut(index);
        return data[index];
    }

    /**
     * 获取第一个元素
     *
     * @return 第一个元素
     */
    public T getFirst() {
        return get(0);
    }

    /**
     * 获取最后一个元素
     *
     * @return 最后一个元素
     */
    public T getLast() {
        return get(size - 1);
    }

    // ===================== 删除 =====================

    /**
     * 清除所有元素
     */
    public void clear() {
        lock.lock();
        try {
            data = (T[]) new Object[DEFAULT_CAPACITY];
            size = 0;
            roundIndex = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 移除指定位置的元素
     *
     * @param index 要移除的元素索引
     * @return 被移除的元素
     */
    public T remove(int index) {
        lock.lock();
        try {
            int len = size;
            if (index < 0 || index >= len) {
                throw new IndexOutOfBoundsException(
                        "Index: " + index + ", Size: " + len);
            }

            T[] oldData = data;
            T oldValue = oldData[index];
            int newLen = len - 1;

            T[] newData = (T[]) new Object[oldData.length];
            // 拷贝移除位置之前的元素
            if (index > 0) {
                System.arraycopy(oldData, 0, newData, 0, index);
            }
            // 拷贝移除位置之后的元素
            int tailLen = newLen - index;
            if (tailLen > 0) {
                System.arraycopy(oldData, index + 1, newData, index, tailLen);
            }

            data = newData;
            size = newLen;
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 移除第一个匹配的元素
     *
     * @param obj 要移除的元素
     * @return {@code true} 如果成功移除
     */
    public boolean remove(T obj) {
        lock.lock();
        try {
            int idx = indexOf(obj);
            if (idx >= 0) {
                // indexOf 已经无锁读，但此处已在锁内，直接操作
                int len = size;
                T[] oldData = data;
                int newLen = len - 1;

                T[] newData = (T[]) new Object[oldData.length];
                if (idx > 0) {
                    System.arraycopy(oldData, 0, newData, 0, idx);
                }
                int tailLen = newLen - idx;
                if (tailLen > 0) {
                    System.arraycopy(oldData, idx + 1, newData, idx, tailLen);
                }

                data = newData;
                size = newLen;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // ===================== 修改 =====================

    /**
     * 替换指定位置的元素
     *
     * @param index 索引
     * @param obj   新元素
     * @return 旧元素
     */
    public T set(int index, T obj) {
        lock.lock();
        try {
            checkIndexOut(index);
            T[] oldData = data;
            T oldValue = oldData[index];

            // COW: 创建新数组副本
            T[] newData = Arrays.copyOf(oldData, oldData.length);
            newData[index] = obj;
            data = newData;
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    // ===================== 数组访问 =====================

    /**
     * 返回底层数组引用
     *
     * @return 底层数组
     */
    public T[] arrays() {
        return data;
    }

    /**
     * 设置底层数组（volatile 写，保证可见性）
     *
     * @param arr 新数组
     */
    public void setArray(T[] arr) {
        this.data = arr;
    }

    /**
     * 返回列表元素的数组副本
     *
     * @return 元素数组副本
     */
    public T[] toArray() {
        return Arrays.copyOf(data, size);
    }

    // ===================== 轮询 =====================

    /**
     * 轮询（Round-Robin）获取元素
     *
     * @return 当前轮询位置的元素
     * @throws IndexOutOfBoundsException 如果列表为空
     */
    public T round() {
        int len = size;
        if (len == 0) {
            throw new IndexOutOfBoundsException("List is empty");
        }
        roundIndex = (roundIndex + 1) % len;
        return data[roundIndex];
    }

    // ===================== 迭代器 =====================

    @Override
    public Iterator<T> iterator() {
        // 快照迭代器：捕获当前数组引用，不受后续修改影响
        final T[] snapshot = data;
        final int len = size;
        return new Iterator<T>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < len;
            }

            @Override
            public T next() {
                if (cursor >= len) {
                    throw new NoSuchElementException();
                }
                return snapshot[cursor++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
