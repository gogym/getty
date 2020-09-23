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

/**
 * FastArrayList.java
 *
 * @description:自定义高性能的数组集合
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class FastArrayList<T> {

    /**
     * 当前下标
     */
    private int currentIndex = 0;

    /**
     * 用于存储数据,关键字transient，序列化对象的时候，这个属性就不会被序列化。
     */
    private transient T[] data = null;
    /**
     * 集合的元素个数
     */
    private int size = 0;
    /**
     * 定义一个常量为 10.(后面用于定义默认的集合大小)
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * 有参构造函数
     * 指定数组的大小
     *
     * @param initialCapacity 长度
     */
    public FastArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("非法的集合初始容量值 Illegal Capacity: " +
                    initialCapacity);
        } else {
            //实例化数组
            this.data = (T[]) new Object[initialCapacity];
        }
    }

    /**
     * 无参构造函数
     * 指定数组的初始大小为 10
     */
    public FastArrayList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 1、复制原数组，并扩容一倍
     * 2、复制原数组，并扩容一倍，并在指定位置插入对象
     *
     * @param index 下标
     * @param obj   obj
     */
    public void checkIncrease(int index, T obj) {
        if (size >= data.length) {
            //实例化一个新数组
            T[] newData = (T[]) new Object[size * 2];

            if (index == -1 && obj == null) {
                System.arraycopy(data, 0, newData, 0, size);
            } else {

                System.arraycopy(data, 0, newData, 0, size);
                //将要插入索引位置后面的对象 拷贝。空出指定位置
                System.arraycopy(data, index, newData, index + 1, size - index);
                newData[index] = obj;
            }

            //将 newData 数组赋值给 data数组
            data = newData;
            newData = null;
        }
    }

    /**
     * 获取数组的大小
     *
     * @return int
     */
    public int size() {
        return this.size;
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
        //检查是否需要扩容
        checkIncrease(-1, null);
        data[size++] = obj;
        return true;

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
        return add(size, obj);
    }


    /**
     * 判断给定索引是否越界
     *
     * @param index index
     * @return boolean
     */
    public boolean checkIndexOut(int index) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("指定的索引越界，集合大小为:" + size + ",您指定的索引大小为:" + index);
        }
        return true;
    }

    public boolean add(int index, T obj) {
        //如果给定索引长度刚好等于原数组长度，那么直接在尾部添加进去
        if (index == size) {
            add(obj);
        }
        //checkIndexOut()如果不抛异常，默认 index <=size,且 index > 0
        else if (checkIndexOut(index)) {
            if (size < data.length) {
                System.arraycopy(data, index, data, index + 1, size - index);
                data[index] = obj;
            } else {
                //需要扩容
                checkIncrease(index, obj);
            }
            size++;
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
        return get(size - 1);
    }

    /**
     * 删除所有元素
     */
    public void clear() {
        for (int i = 0; i < data.length; i++) {
            data[i] = null;
        }
    }

    /**
     * 根据索引删除元素
     *
     * @param index index
     * @return T
     */
    public T remove(int index) {
        if (index == size + 1) {
            throw new IndexOutOfBoundsException("指定的索引越界，集合大小为:" + size + ",您指定的索引大小为:" + index);
        } else if (checkIndexOut(index)) {
            //保存对象
            T obj = data[index];
            if (index == size) {
                data[index] = null;
            } else {
                //将后边的数组向前移动一位
                System.arraycopy(data, index + 1, data, index, size - index);
            }
            size--;
            return obj;
        }

        return null;
    }

    /**
     * 删除指定的元素，删除成功返回 true，失败返回 false
     *
     * @param obj obj
     * @return boolean
     */
    public boolean remove(T obj) {
        for (int i = 0; i < size; i++) {
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
    public T change(int index, T obj) {
        checkIndexOut(index);
        T oldObj = data[index];
        data[index] = obj;
        return oldObj;
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


    /**
     * 轮训，均衡的随机获取数组里面的元素
     *
     * @return
     */
    public T round() {
        currentIndex = (currentIndex + 1) % this.size;
        return this.get(currentIndex);
    }

}
