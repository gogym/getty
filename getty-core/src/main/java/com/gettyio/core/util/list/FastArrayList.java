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
package com.gettyio.core.util.list;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 高性能数组列表（非线程安全）。
 * <p>
 * 与 {@link java.util.ArrayList} 相比，提供更直接的数组访问、
 * 轮询（round-robin）负载均衡等特性。适用于单线程或外部同步场景。
 * </p>
 *
 * @param <T> 元素类型
 * @author gogym
 * @date 2020/4/9
 */
public class FastArrayList<T> implements Iterable<T> {

    /** 默认初始容量 */
    private static final int DEFAULT_CAPACITY = 8;

    /** 轮询索引，用于 {@link #round()} 方法 */
    private int roundIndex = 0;

    /** 底层数据存储（transient，序列化时不自动序列化） */
    private transient T[] data;

    /** 实际元素数量 */
    private int size = 0;

    // ===================== 构造函数 =====================

    /**
     * 指定初始容量构造
     *
     * @param initialCapacity 初始容量
     */
    public FastArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
        }
        this.data = (T[]) new Object[initialCapacity];
    }

    /**
     * 默认容量构造
     */
    public FastArrayList() {
        this(DEFAULT_CAPACITY);
    }

    // ===================== 容量管理 =====================

    /**
     * 确保底层数组至少有 minCapacity 个槽位。
     * <p>
     * 扩容策略：1.5 倍增长（{@code oldCap + oldCap >> 1}），
     * 相比 2 倍增长，内存利用率更高，减少过度分配。
     * </p>
     *
     * @param minCapacity 期望的最小容量
     */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int oldCapacity = data.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            if (newCapacity < DEFAULT_CAPACITY) {
                newCapacity = DEFAULT_CAPACITY;
            }
            T[] newData = (T[]) new Object[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

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
     * 查找元素在列表中的索引（正向遍历）
     *
     * @param o 目标元素
     * @return 索引，未找到返回 -1
     */
    public int indexOf(T o) {
        if (o == null) {
            for (int i = 0; i < size; i++) {
                if (data[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (o.equals(data[i])) {
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
        if (size >= data.length) {
            ensureCapacity(size + 1);
        }
        data[size++] = obj;
        return true;
    }

    /**
     * 在首部添加元素
     *
     * @param obj 要添加的元素
     * @return 始终返回 {@code true}
     */
    public boolean addFirst(T obj) {
        ensureCapacity(size + 1);
        if (size > 0) {
            System.arraycopy(data, 0, data, 1, size);
        }
        data[0] = obj;
        size++;
        return true;
    }

    /**
     * 在尾部添加元素（快速路径）。
     *
     * @param obj 要添加的元素
     * @return 始终返回 {@code true}
     */
    public boolean addLast(T obj) {
        ensureCapacity(size + 1);
        data[size++] = obj;
        return true;
    }

    /**
     * 在指定位置插入元素
     *
     * @param index 插入位置
     * @param obj   要插入的元素
     * @return 始终返回 {@code true}
     */
    public boolean add(int index, T obj) {
        if (index == size) {
            // 尾部追加快速路径
            ensureCapacity(size + 1);
            data[size++] = obj;
        } else {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException(
                        "Index: " + index + ", Size: " + size);
            }
            ensureCapacity(size + 1);
            System.arraycopy(data, index, data, index + 1, size - index);
            data[index] = obj;
            size++;
        }
        return true;
    }

    // ===================== 索引校验 =====================

    /**
     * 校验索引是否越界（允许 index == size，用于尾部追加）
     *
     * @param index 待校验的索引
     * @throws IndexOutOfBoundsException 如果索引越界
     */
    public void checkIndex(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Size: " + size);
        }
    }

    // ===================== 获取 =====================

    /**
     * 获取指定位置的元素
     *
     * @param index 索引
     * @return 对应元素
     */
    public T get(int index) {
        checkIndex(index);
        return data[index];
    }

    /**
     * 获取第一个元素
     *
     * @return 第一个元素
     * @throws IndexOutOfBoundsException 如果列表为空
     */
    public T getFirst() {
        return get(0);
    }

    /**
     * 获取最后一个元素
     *
     * @return 最后一个元素
     * @throws IndexOutOfBoundsException 如果列表为空
     */
    public T getLast() {
        return get(size - 1);
    }

    // ===================== 删除 =====================

    /**
     * 清除所有元素并重置大小
     */
    public void clear() {
        for (int i = 0; i < size; i++) {
            data[i] = null;
        }
        size = 0;
        roundIndex = 0;
    }

    /**
     * 移除指定位置的元素
     *
     * @param index 要移除的元素索引
     * @return 被移除的元素
     */
    public T remove(int index) {
        checkIndex(index);
        T obj = data[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(data, index + 1, data, index, numMoved);
        }
        data[--size] = null; // 帮助 GC
        return obj;
    }

    /**
     * 移除第一个匹配的元素
     *
     * @param obj 要移除的元素
     * @return {@code true} 如果成功移除
     */
    public boolean remove(T obj) {
        int idx = indexOf(obj);
        if (idx >= 0) {
            remove(idx);
            return true;
        }
        return false;
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
        checkIndex(index);
        T oldObj = data[index];
        data[index] = obj;
        return oldObj;
    }

    // ===================== 数组访问 =====================

    /**
     * 返回底层数组引用（修改会影响列表本身）
     *
     * @return 底层数组
     */
    public T[] arrays() {
        return data;
    }

    /**
     * 返回列表元素的数组副本（修改不影响列表本身）
     *
     * @return 元素数组副本
     */
    public T[] toArray() {
        return Arrays.copyOf(data, size);
    }

    // ===================== 轮询 =====================

    /**
     * 轮询（Round-Robin）获取元素。
     * <p>
     * 每次调用返回下一个元素，到达末尾后从头开始。
     * 适用于负载均衡场景。
     * </p>
     *
     * @return 当前轮询位置的元素
     * @throws IndexOutOfBoundsException 如果列表为空
     */
    public T round() {
        if (size == 0) {
            throw new IndexOutOfBoundsException("List is empty");
        }
        int idx = roundIndex;
        if (++roundIndex >= size) {
            roundIndex = 0;
        }
        return data[idx];
    }

    // ===================== 迭代器 =====================

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public T next() {
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return data[cursor++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
