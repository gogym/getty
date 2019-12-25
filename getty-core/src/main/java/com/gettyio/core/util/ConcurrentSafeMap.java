package com.gettyio.core.util;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * 类名：ConcurrentSafeMap
 * 版权：Copyright by www.getty.com
 * 描述：为了获取更好的性能表现，自定义一个读写安全的map
 * 修改人：gogym
 * 时间：2019/12/24
 */
public class ConcurrentSafeMap<K, V> {

    private final Map<K, V> map;
    //可重入读写锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    //读锁
    private final Lock r = lock.readLock();
    //写锁
    private final Lock w = lock.writeLock();

    public ConcurrentSafeMap() {
        this.map = new HashMap<>();
    }

    public ConcurrentSafeMap(Map<K, V> map) {
        this.map = map;
    }

    //获取一个值
    public V get(Object key) {
        r.lock();
        try {
            return map.get(key);
        } finally {
            r.unlock();
        }
    }

    //设置一个值
    public V put(K key, V value) {
        w.lock();
        try {
            return map.put(key, value);
        } finally {
            w.unlock();
        }
    }

    //移除一个值
    public V remove(Object key) {
        w.lock();
        try {
            return map.remove(key);
        } finally {
            w.unlock();
        }
    }

    //添加全部
    public void putAll(Map<? extends K, ? extends V> m) {
        w.lock();
        try {
            map.putAll(m);
        } finally {
            w.unlock();
        }
    }

    //清除全部
    public void clear() {
        w.lock();
        try {
            map.clear();
        } finally {
            w.unlock();
        }
    }

    //返回大小
    public int size() {
        r.lock();
        try {
            return map.size();
        } finally {
            r.unlock();
        }
    }

    //非空
    public boolean isEmpty() {
        r.lock();
        try {
            return map.isEmpty();
        } finally {
            r.unlock();
        }
    }

    public boolean containsKey(Object key) {
        r.lock();
        try {
            return map.containsKey(key);
        } finally {
            r.unlock();
        }
    }

    public boolean containsValue(Object value) {
        r.lock();
        try {
            return map.containsValue(value);
        } finally {
            r.unlock();
        }
    }

}
