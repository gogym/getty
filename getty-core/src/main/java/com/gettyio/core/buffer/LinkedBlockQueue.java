package com.gettyio.core.buffer;/*
 * 类名：LinkedBlockArray
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/13
 */

import java.lang.reflect.Array;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedBlockQueue<T> {

    /**
     * 队列实现
     */
    T[] items;

    //初始大小
    int capacity=1024;

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
        items = (T[])new Object[capacity];
        this.capacity=capacity;
    }

    private  <T> T[] getArray(Class<T> componentType, int length) {
        return (T[]) Array.newInstance(componentType, length);
    }


    /**
     * 进队 插入最后一个元素位置
     *
     * @param t 泛型
     * @throws InterruptedException 异常
     */
    public void put(T t) throws InterruptedException {
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
    }

    /**
     * 出队 最后一个元素
     *
     * @return T
     */
    public T poll() throws InterruptedException {
        lock.lock();
        T t;
        try {
            while (count == 0) {
               //出队阻塞
                notEmpty.await();
            }
            t = this.items[removeIndex];
            this.items[removeIndex] = null;
            if (++removeIndex == items.length) {
                removeIndex = 0;
            }
            count--;
            notFull.signal();
        } finally {
            lock.unlock();
        }
        return t;
    }

    private void checkNull(T t) {
        if (t == null) {
            throw new NullPointerException();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCount() {
        return count;
    }
}
