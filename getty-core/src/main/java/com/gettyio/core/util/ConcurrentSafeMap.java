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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于 {@link ReadWriteLock} 实现的高性能线程安全 Map。
 * <p>
 * 读操作之间完全并发，写操作独占锁。在读多写少场景下，
 * 性能显著优于 {@link java.util.concurrent.ConcurrentHashMap} 的 synchronized 方案。
 * </p>
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author gogym
 * @date 2020/4/9
 */
public class ConcurrentSafeMap<K, V> {

    /** 底层存储 */
    private final Map<K, V> map;

    /** 可重入读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** 读锁 —— 允许多个线程同时读取 */
    private final Lock readLock = lock.readLock();

    /** 写锁 —— 独占访问 */
    private final Lock writeLock = lock.writeLock();

    /**
     * 构造一个空的线程安全 Map
     */
    public ConcurrentSafeMap() {
        this.map = new HashMap<>();
    }

    /**
     * 使用已有的 Map 初始化（注意：不会拷贝，直接引用）
     *
     * @param map 初始 Map
     */
    public ConcurrentSafeMap(Map<K, V> map) {
        this.map = map;
    }

    /**
     * 获取指定 key 对应的 value
     *
     * @param key 键
     * @return 对应的值，不存在则返回 {@code null}
     */
    public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 插入或更新键值对
     *
     * @param key   键
     * @param value 值
     * @return 旧值，不存在则返回 {@code null}
     */
    public V put(K key, V value) {
        writeLock.lock();
        try {
            return map.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 移除指定 key 对应的键值对
     *
     * @param key 键
     * @return 被移除的值，不存在则返回 {@code null}
     */
    public V remove(Object key) {
        writeLock.lock();
        try {
            return map.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 批量添加键值对
     *
     * @param m 要添加的 Map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        writeLock.lock();
        try {
            map.putAll(m);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 清除所有键值对
     */
    public void clear() {
        writeLock.lock();
        try {
            map.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 返回当前键值对数量
     *
     * @return Map 大小
     */
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断 Map 是否为空
     *
     * @return {@code true} 如果 Map 不含任何键值对
     */
    public boolean isEmpty() {
        readLock.lock();
        try {
            return map.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断是否包含指定 key
     *
     * @param key 键
     * @return {@code true} 如果包含该 key
     */
    public boolean containsKey(Object key) {
        readLock.lock();
        try {
            return map.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断是否包含指定 value
     *
     * @param value 值
     * @return {@code true} 如果包含该 value
     */
    public boolean containsValue(Object value) {
        readLock.lock();
        try {
            return map.containsValue(value);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 原子性的 putIfAbsent 操作。
     * <p>
     * 如果 key 不存在，则插入并返回 {@code null}；
     * 如果 key 已存在，则返回现有值。
     * 整个操作在写锁保护下原子执行。
     * </p>
     *
     * @param key   键
     * @param value 值
     * @return 已存在的值，或 {@code null}（表示新插入）
     */
    public V putIfAbsent(K key, V value) {
        writeLock.lock();
        try {
            V existing = map.get(key);
            if (existing == null) {
                map.put(key, value);
            }
            return existing;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 返回所有 value 的快照副本。
     * <p>
     * 在读锁保护下拷贝 value 集合，返回的 Collection 是独立副本，
     * 后续对 Map 的修改不会影响返回结果。
     * </p>
     *
     * @return value 集合的快照
     */
    public Collection<V> values() {
        readLock.lock();
        try {
            return new ArrayList<>(map.values());
        } finally {
            readLock.unlock();
        }
    }
}
