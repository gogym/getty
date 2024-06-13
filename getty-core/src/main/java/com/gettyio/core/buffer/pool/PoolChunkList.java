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
package com.gettyio.core.buffer.pool;


import com.gettyio.core.buffer.bytebuf.impl.PooledByteBuf;
import com.gettyio.core.util.StringUtil;

/**
 * 内存块列表
 *
 * @param <T>
 */
final class PoolChunkList<T> {
    /**
     * 池化空间
     */
    private final PoolArena<T> arena;
    /**
     * 下一个块列表
     */
    private final PoolChunkList<T> nextList;
    /**
     * 上一个块列表
     */
    PoolChunkList<T> prevList;

    /**
     * 最小使用率
     */
    private final int minUsage;
    /**
     * 最大使用率
     */
    private final int maxUsage;

    /**
     * 池化堆内存块
     */
    private PoolChunk<T> head;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage) {
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
    }

    /**
     * 创建一个内存块列表
     *
     * @param buf
     * @param reqCapacity
     * @param normCapacity
     * @return
     */
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (head == null) {
            return false;
        }

        for (PoolChunk<T> cur = head; ; ) {
            long handle = cur.allocate(normCapacity);
            if (handle < 0) {
                cur = cur.next;
                if (cur == null) {
                    return false;
                }
            } else {
                cur.initBuf(buf, handle, reqCapacity);
                if (cur.usage() >= maxUsage) {
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
    }


    /**
     * 释放池块中的特定资源。
     *
     * 此方法将指定的资源从池块中释放，并根据池块的使用情况决定是否从列表中移除该池块。
     * 如果池块的使用率低于最小使用率，且该池块是当前列表中的第一个池块，那么将销毁这个池块。
     * 否则，将该池块移动到前一个列表中，以备后续使用。
     *
     * @param chunk 要释放资源的池块。
     * @param handle 要释放的具体资源的句柄。
     */
    void free(PoolChunk<T> chunk, long handle) {
        // 释放指定的资源。
        chunk.free(handle);
        // 检查池块的当前使用率是否低于最小使用率。
        if (chunk.usage() < minUsage) {
            // 如果使用率低于最小使用率，从当前列表中移除该池块。
            remove(chunk);
            // 检查前一个列表是否为空。
            if (prevList == null) {
                // 如果前一个列表为空，断言该池块的使用率为0，然后销毁池块。
                assert chunk.usage() == 0;
                arena.destroyChunk(chunk);
            } else {
                // 如果前一个列表不为空，将池块添加到前一个列表中。
                prevList.add(chunk);
            }
        }
    }


    /**
     * 将一个池块添加到当前池列表中。
     * 如果池块的使用率超过了最大使用率，则将其转移到下一个列表中。
     * 否则，将池块添加到当前列表的头部。
     *
     * @param chunk 要添加到池列表的池块。
     */
    void add(PoolChunk<T> chunk) {
        // 检查池块的使用率是否超过最大使用率
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk); // 如果超过，将其添加到下一个列表中
            return;
        }

        // 将当前列表设置为池块的父列表
        chunk.parent = this;
        // 如果当前列表为空，将池块设置为头部，并初始化其链接
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            // 如果当前列表不为空，将池块插入头部，并更新链接
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    /**
     * 移除内存块
     *
     * @param cur
     */
    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    @Override
    public String toString() {
        if (head == null) {
            return "none";
        }

        StringBuilder buf = new StringBuilder();
        for (PoolChunk<T> cur = head; ; ) {
            buf.append(cur);
            cur = cur.next;
            if (cur == null) {
                break;
            }
            buf.append(StringUtil.NEWLINE);
        }

        return buf.toString();
    }
}
