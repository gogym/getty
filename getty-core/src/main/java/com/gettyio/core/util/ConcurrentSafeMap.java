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
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 基于 {@link StampedLock} 乐观读实现的高性能线程安全 Map。
 * <p>
 * 读操作优先使用乐观读（optimistic read），无需真正获取锁，
 * 仅在读后校验 stamp 是否被写操作篡改。在读多写少场景下，
 * 性能显著优于 {@link java.util.concurrent.ConcurrentHashMap}
 * 和基于 {@link java.util.concurrent.locks.ReentrantReadWriteLock} 的方案。
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

    /**
     *  stamped 锁，支持乐观读。
     *  读操作无需加锁（仅记录 stamp），写操作独占锁。
     *  相比 ReentrantReadWriteLock，避免了 CAS + park/unpark 的开销。
     */
    private final StampedLock lock = new StampedLock();

    /**
     * 构造一个空的线程安全 Map
     */
    public ConcurrentSafeMap() {
        this.map = new HashMap<>();
    }

    /**
     * 使用已有的 Map 初始化（注意：不会拷贝，直接引用）。
     * <p>
     * 警告：传入的 Map 不应当被其他线程直接修改，否则会破坏线程安全性。
     * </p>
     *
     * @param map 初始 Map
     */
    public ConcurrentSafeMap(Map<K, V> map) {
        this.map = map;
    }

    // ===================== 读操作（乐观读） =====================

    /**
     * 获取指定 key 对应的 value。
     * <p>
     * 优先使用乐观读，无需获取锁；若读期间有写操作则升级为悲观读锁重试。
     * </p>
     *
     * @param key 键
     * @return 对应的值，不存在则返回 {@code null}
     */
    public V get(Object key) {
        long stamp = lock.tryOptimisticRead();
        V result = map.get(key);
        if (lock.validate(stamp)) {
            return result;
        }
        // 乐观读失败，升级为悲观读锁重试
        stamp = lock.readLock();
        try {
            return map.get(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 获取指定 key 对应的 value，不存在则返回默认值。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 对应的值，不存在则返回 {@code defaultValue}
     */
    public V getOrDefault(Object key, V defaultValue) {
        long stamp = lock.tryOptimisticRead();
        V result = map.get(key);
        if (lock.validate(stamp)) {
            return result != null ? result : defaultValue;
        }
        stamp = lock.readLock();
        try {
            result = map.get(key);
            return result != null ? result : defaultValue;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 返回当前键值对数量
     *
     * @return Map 大小
     */
    public int size() {
        long stamp = lock.tryOptimisticRead();
        int result = map.size();
        if (lock.validate(stamp)) {
            return result;
        }
        stamp = lock.readLock();
        try {
            return map.size();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 判断 Map 是否为空
     *
     * @return {@code true} 如果 Map 不含任何键值对
     */
    public boolean isEmpty() {
        long stamp = lock.tryOptimisticRead();
        boolean result = map.isEmpty();
        if (lock.validate(stamp)) {
            return result;
        }
        stamp = lock.readLock();
        try {
            return map.isEmpty();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 判断是否包含指定 key
     *
     * @param key 键
     * @return {@code true} 如果包含该 key
     */
    public boolean containsKey(Object key) {
        long stamp = lock.tryOptimisticRead();
        boolean result = map.containsKey(key);
        if (lock.validate(stamp)) {
            return result;
        }
        stamp = lock.readLock();
        try {
            return map.containsKey(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 判断是否包含指定 value
     *
     * @param value 值
     * @return {@code true} 如果包含该 value
     */
    public boolean containsValue(Object value) {
        long stamp = lock.tryOptimisticRead();
        boolean result = map.containsValue(value);
        if (lock.validate(stamp)) {
            return result;
        }
        stamp = lock.readLock();
        try {
            return map.containsValue(value);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    // ===================== 写操作（独占写锁） =====================

    /**
     * 插入或更新键值对
     *
     * @param key   键
     * @param value 值
     * @return 旧值，不存在则返回 {@code null}
     */
    public V put(K key, V value) {
        long stamp = lock.writeLock();
        try {
            return map.put(key, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 移除指定 key 对应的键值对
     *
     * @param key 键
     * @return 被移除的值，不存在则返回 {@code null}
     */
    public V remove(Object key) {
        long stamp = lock.writeLock();
        try {
            return map.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 批量添加键值对
     *
     * @param m 要添加的 Map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        long stamp = lock.writeLock();
        try {
            map.putAll(m);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 清除所有键值对
     */
    public void clear() {
        long stamp = lock.writeLock();
        try {
            map.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // ===================== 复合操作 =====================

    /**
     * 原子性的 putIfAbsent 操作。
     * <p>
     * 如果 key 不存在（包括 key 存在但 value 为 null 的情况不视为"已存在"），
     * 则插入并返回 {@code null}；如果 key 已存在，则返回现有值。
     * 整个操作在写锁保护下原子执行。
     * </p>
     *
     * @param key   键
     * @param value 值
     * @return 已存在的值，或 {@code null}（表示新插入）
     */
    public V putIfAbsent(K key, V value) {
        long stamp = lock.writeLock();
        try {
            if (!map.containsKey(key)) {
                map.put(key, value);
                return null;
            }
            return map.get(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 原子性的 computeIfAbsent 操作。
     * <p>
     * 如果 key 不存在，则使用 mappingFunction 计算值并插入；
     * 如果 key 已存在，则返回现有值。
     * 优先使用乐观读检查，仅在 key 确实不存在时才获取写锁。
     * </p>
     *
     * @param key             键
     * @param mappingFunction 计算值的函数
     * @return 已存在的值或新计算的值
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // 先尝试乐观读快速路径
        long stamp = lock.tryOptimisticRead();
        V result = map.get(key);
        if (lock.validate(stamp) && result != null) {
            return result;
        }
        // key 不存在或乐观读失败，获取写锁
        stamp = lock.writeLock();
        try {
            result = map.get(key);
            if (result == null) {
                result = mappingFunction.apply(key);
                if (result != null) {
                    map.put(key, result);
                }
            }
            return result;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 原子性的 replace 操作。仅当 key 当前映射到 oldValue 时才替换为 newValue。
     *
     * @param key      键
     * @param oldValue 期望的当前值
     * @param newValue 新值
     * @return {@code true} 如果替换成功
     */
    public boolean replace(K key, V oldValue, V newValue) {
        long stamp = lock.writeLock();
        try {
            V curValue = map.get(key);
            if (curValue == null) {
                return oldValue == null && map.containsKey(key);
            }
            if (curValue == oldValue || curValue.equals(oldValue)) {
                map.put(key, newValue);
                return true;
            }
            return false;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 原子性的 replace 操作。仅当 key 当前存在时才替换。
     *
     * @param key   键
     * @param value 新值
     * @return 旧值，不存在则返回 {@code null}
     */
    public V replace(K key, V value) {
        long stamp = lock.writeLock();
        try {
            if (map.containsKey(key)) {
                return map.put(key, value);
            }
            return null;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // ===================== 遍历 =====================

    /**
     * 在锁保护下遍历所有键值对，对每个条目执行给定操作。
     * <p>
     * 使用写锁保证遍历期间数据一致性，避免 {@link #values()} 的拷贝开销。
     * 适用于需要遍历全部条目的场景。
     * </p>
     *
     * @param action 对每个键值对执行的操作
     */
    public void forEach(BiConsumer<? super K, ? super V> action) {
        long stamp = lock.writeLock();
        try {
            for (Map.Entry<K, V> entry : map.entrySet()) {
                action.accept(entry.getKey(), entry.getValue());
            }
        } finally {
            lock.unlockWrite(stamp);
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
        long stamp = lock.readLock();
        try {
            return new ArrayList<>(map.values());
        } finally {
            lock.unlockRead(stamp);
        }
    }
}
