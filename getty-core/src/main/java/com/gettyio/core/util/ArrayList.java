package com.gettyio.core.util;/*
 * 类名：ArrayList
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/26
 */

public class ArrayList<T> {

    //用于存储数据
    //关键字transient，序列化对象的时候，这个属性就不会被序列化。
    private transient T[] data = null;
    //集合的元素个数
    private int size = 0;
    //定义一个常量为 10.(后面用于定义默认的集合大小)
    private static final int DEFAULT_CAPACITY = 10;

    /***
     * 有参构造函数
     * 指定数组的大小
     * @param initialCapacity 长度
     */
    public ArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("非法的集合初始容量值 Illegal Capacity: " +
                    initialCapacity);
        } else {
            //实例化数组
            this.data = (T[]) new Object[initialCapacity];
        }
    }

    /***
     * 无参构造函数
     * 指定数组的初始大小为 10
     */
    public ArrayList() {
        this(DEFAULT_CAPACITY);
    }

    /***
     * 1、复制原数组，并扩容一倍
     * 2、复制原数组，并扩容一倍，并在指定位置插入对象
     * @param index 下标
     * @param obj obj
     */
    public void checkIncrease(int index, T obj) {
        if (size >= data.length) {
            //实例化一个新数组
            T[] newData = (T[]) new Object[size * 2];

            if (index == -1 && obj == null) {
                System.arraycopy(data, 0, newData, 0, size);
            } else {
                //将要插入索引位置前面的对象 拷贝
                System.arraycopy(data, index, newData, index + 1, size - index);
            }

            //将 newData 数组赋值给 data数组
            data = newData;
            newData = null;
        }
    }

    /***
     * 获取数组的大小
     * @return int
     */
    public int getSize() {
        return this.size;
    }

    /***
     * 根据元素获得在集合中的索引
     * @param o o
     * @return int
     */
    public int indexOf(T o) {
        if (o == null) {
            for (int i = 0; i < data.length; i++)
                if (data[i] == null)
                    return i;
        } else {
            for (int i = 0; i < data.length; i++)
                if (o.equals(data[i]))
                    return i;
        }
        return -1;
    }

    /***
     * 在尾部添加元素
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

    /***
     * 根据索引获得元素
     * @param index index
     * @return T
     */
    public T get(int index) {
        checkIndexOut(index);
        return data[index];

    }

    /***
     * 删除所有元素
     */
    public void removeAll() {
        for (int i = 0; i < data.length; i++) {
            data[i] = null;
        }
    }

    /***
     * 根据索引删除元素
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

    /***
     * 删除指定的元素，删除成功返回 true，失败返回 false
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

    /***
     * 在指定位置修改元素，通过索引，修改完成后返回原数据
     * @param index index
     * @param obj obj
     * @return T
     */
    public T change(int index, T obj) {
        checkIndexOut(index);
        T oldObj = data[index];
        data[index] = obj;
        return oldObj;
    }

    /***
     * 查看集合中是否包含某个元素，如果有，返回 true，没有返回 false
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
}
