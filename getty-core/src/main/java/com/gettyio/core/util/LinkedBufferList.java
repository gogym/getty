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

import com.gettyio.core.buffer.pool.PooledByteBuffer;

import java.util.List;

/**
 * 基于 Entry 单向链表的缓冲区列表。
 * <p>
 * 每个 Entry 持有一个 {@link PooledByteBuffer}，通过 next 指针
 * 串联成单向链表。所有操作均线程安全（synchronized 保护）。
 * </p>
 * <ul>
 *   <li>{@link #offer} — O(1) 尾部追加</li>
 *   <li>{@link #poll} — O(1) 头部弹出</li>
 *   <li>{@link #pollAll} — O(n) 批量弹出</li>
 * </ul>
 *
 * @author gogym
 */
public class LinkedBufferList {

    /**
     * 单向链表节点
     */
    private static final class Entry {
        final PooledByteBuffer buf;
        Entry next;

        Entry(PooledByteBuffer buf) {
            this.buf = buf;
        }
    }

    /** 链表头指针（哨兵节点，不存储数据） */
    private final Entry head = new Entry(null);

    /** 链表尾指针（始终指向最后一个有效节点） */
    private Entry tail = head;

    /** 链表中待写出的 Entry 数量 */
    private int count;

    /**
     * 将缓冲区追加到链表尾部。O(1)
     *
     * @param buf 待缓存的缓冲区
     */
    public synchronized void offer(PooledByteBuffer buf) {
        Entry entry = new Entry(buf);
        tail.next = entry;
        tail = entry;
        count++;
    }

    /**
     * 从链表头部弹出一个缓冲区。
     * <p>非阻塞：链表为空时立即返回 null。</p>
     *
     * @return 缓冲区，链表为空时返回 null
     */
    public synchronized PooledByteBuffer poll() {
        if (count == 0) {
            return null;
        }
        Entry first = head.next;
        head.next = first.next;
        if (first.next == null) {
            tail = head;
        }
        first.next = null; // 帮助 GC
        count--;
        return first.buf;
    }

    /**
     * 从链表中批量弹出所有可用缓冲区。
     *
     * @param list 用于接收弹出元素的列表（调用方传入，避免每次分配）
     */
    public synchronized void pollAll(List<PooledByteBuffer> list) {
        Entry e = head.next;
        while (e != null) {
            list.add(e.buf);
            e = e.next;
        }
        // 重置链表
        head.next = null;
        tail = head;
        count = 0;
    }

    /**
     * 从链表中批量弹出最多 maxCount 个缓冲区。
     *
     * @param list     用于接收弹出元素的列表
     * @param maxCount 最多弹出数量
     */
    public synchronized void pollAll(List<PooledByteBuffer> list, int maxCount) {
        Entry e = head.next;
        int removed = 0;
        while (e != null && removed < maxCount) {
            list.add(e.buf);
            Entry next = e.next;
            e.next = null; // 帮助 GC
            e = next;
            removed++;
        }
        // 重新链接剩余部分
        head.next = e;
        if (e == null) {
            tail = head;
        }
        count -= removed;
    }

    /**
     * 获取链表中缓冲区的数量。
     *
     * @return 缓冲区数量
     */
    public synchronized int size() {
        return count;
    }

    /**
     * 清空链表并释放所有残留缓冲区。
     * <p>
     * 遍历链表，逐个调用 {@link PooledByteBuffer#release()}，然后重置链表状态。
     * </p>
     */
    public synchronized void drainAndRelease() {
        Entry e = head.next;
        while (e != null) {
            Entry next = e.next;
            e.buf.release();
            e.next = null; // 帮助 GC
            e = next;
        }
        head.next = null;
        tail = head;
        count = 0;
    }
}
