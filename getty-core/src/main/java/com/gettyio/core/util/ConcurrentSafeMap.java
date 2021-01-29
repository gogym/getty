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


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * ConcurrentSafeMap.java
 *
 * @description:为了获取更好的性能表现，自定义一个读写安全的map
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
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


    public V putIfAbsent(K key, V value) {

        if (containsKey(key)) {
            return get(key);
        } else {
            put(key, value);
            return null;
        }
    }

    public Collection<V> values() {
        return map.values();
    }


}
