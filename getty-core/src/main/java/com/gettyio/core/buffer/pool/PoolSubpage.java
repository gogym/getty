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

/**
 * PoolChunk 包含一列PoolSubpage
 *
 * @param <T>
 */
final class PoolSubpage<T> {
    /**
     * 所属的PoolChunk
     */
    final PoolChunk<T> chunk;
    /**
     * 在memoryMap的索引 id memoryMap[id]
     */
    private final int memoryMapIdx;
    /**
     * 大小
     */
    private final int pageSize;

    /**
     * bitmap中实际使用的长度
     */
    private int bitmapLength;
    private final long[] bitmap;

    /**
     * 前后两个相连的子页
     */
    PoolSubpage<T> prev;
    PoolSubpage<T> next;
    /**
     * 是否销毁
     */
    boolean doNotDestroy;
    /**
     * 元素大小
     */
    int elemSize;
    /**
     * 最大元素个数
     */
    private int maxNumElems;

    /**
     * 下一个可分配的elemSize块
     */
    private int nextAvail;
    /**
     * 目前可用的elemSize块数量。
     */
    private int numAvail;

    /**
     * 创建链表头的特殊构造函数
     */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
        init(elemSize);
    }

    void init(int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;
            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                bitmapLength++;
            }

            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;
            }
        }
        addToPool();
    }

    /**
     * 返回子页分配的位图索引。
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) == 0;
        bitmap[q] |= 1L << r;

        if (--numAvail == 0) {
            removeFromPool();
        }

        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} 如果这个子页面正在使用。
     * {@code false} 如果这个子页面没有被它的块使用，那么就可以释放它。
     */
    boolean free(int bitmapIdx) {

        if (elemSize == 0) {
            return true;
        }

        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        setNextAvail(bitmapIdx);

        if (numAvail++ == 0) {
            addToPool();
            return true;
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {
            // 子页面未使用 (numAvail == maxNumElems)
            if (prev == next) {
                // 如果此子页是池中仅存的子页，则不要删除。
                return true;
            }

            // 如果池中还有其他子页，则将此子页从池中删除。
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    //----------------------------------

    private void addToPool() {
        PoolSubpage<T> head = chunk.arena.findSubpagePoolHead(elemSize);
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

}
