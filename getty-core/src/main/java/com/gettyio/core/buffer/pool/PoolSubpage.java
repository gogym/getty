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
     * 子页所归属的池块。
     * 池块是内存池中管理物理内存的单位，子页是池块内部的细分管理单元。
     */
    final PoolChunk<T> chunk;

    /**
     * 子页在内存映射表中的索引。
     * 用于快速定位子页在物理内存中的位置，提高内存的管理效率。
     */
    private final int memoryMapIdx;

    /**
     * 每页的大小。
     * 用于计算子页中元素的数量，以及管理位图的大小。
     */
    private final int pageSize;

    /**
     * 位图的长度。
     * 位图用于标记子页中每个元素的可用状态，长度根据页面大小计算得出。
     */
    private int bitmapLength;

    /**
     * 存储位图的数组。
     * 位图中每个位对应子页中的一个元素，位的值指示元素是否可用。
     */
    private final long[] bitmap;

    /**
     * 子页链表中的前一个子页。
     * 形成链表用于管理同一池块中的多个子页，方便元素的分配和回收。
     */
    PoolSubpage<T> prev;
    /**
     * 子页链表中的下一个子页。
     * 同prev字段，用于链表式管理子页。
     */
    PoolSubpage<T> next;

    /**
     * 标记是否阻止销毁。
     * 用于在特定情况下防止子页被销毁，确保资源的安全管理。
     */
    boolean doNotDestroy;

    /**
     * 元素的大小。
     * 用于计算子页中可以容纳的元素数量，确保内存的合理利用。
     */
    int elemSize;

    /**
     * 子页中最大元素数量。
     * 根据页面大小和元素大小计算得出，用于管理子页中的元素数量。
     */
    private int maxNumElems;

    /**
     * 下一个可用元素的索引。
     * 用于快速定位下一个可用的元素，提高元素的分配效率。
     */
    private int nextAvail;

    /**
     * 子页中当前可用的元素数量。
     * 用于管理子页中的元素，方便元素的分配和回收。
     */
    private int numAvail;


    /**
     * PoolSubpage的构造函数。
     *
     * 初始化一个PoolSubpage对象，该对象用于管理内存池中的一个子页。子页是内存池划分的较小单位，
     * 通过构造函数设置子页的大小，并初始化相关的成员变量。
     *
     * @param pageSize 子页的大小。这个参数决定了子页能够管理的内存大小。
     */
    PoolSubpage(int pageSize) {
        // 初始化chunk为null，表示当前子页还没有关联任何内存块。
        chunk = null;
        // 初始化memoryMapIdx为-1，表示当前子页在内存映射表中的索引尚未确定。
        memoryMapIdx = -1;
        // 初始化elemSize为-1，表示当前子页管理的元素大小尚未设置。
        elemSize = -1;
        // 设置pageSize为传入的参数值，用于后续管理子页的内存分配。
        this.pageSize = pageSize;
        // 初始化bitmap为null，bitmap用于管理子页中内存块的使用状态。
        bitmap = null;
    }

    PoolSubpage(PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
        init(elemSize);
    }

    /**
     * 初始化内存池。
     *
     * 此方法用于初始化内存池的相关参数和数据结构，以备后续的内存分配使用。
     * 它通过计算和设置内存池中元素的最大数量、当前可用元素的数量、位图的长度等，
     * 来配置内存池的行为和状态。
     *
     * @param elemSize 每个元素的大小。这个参数决定了内存池中每个元素的占用空间，
     *                 从而影响了内存池可以容纳的最大元素数量。
     */
    void init(int elemSize) {
        // 设置标志位，防止内存池被销毁
        doNotDestroy = true;
        // 设置元素大小
        this.elemSize = elemSize;
        // 如果元素大小不为0，则进行进一步的初始化配置
        if (elemSize != 0) {
            // 根据页面大小和元素大小计算出最多可以容纳的元素数量
            maxNumElems = numAvail = pageSize / elemSize;
            // 设置下一个可用元素的起始索引
            nextAvail = 0;
            // 计算位图的长度，位图用于标记元素的使用状态
            bitmapLength = maxNumElems >>> 6;
            // 如果最大元素数量不是64的整数倍，需要增加一位图来覆盖剩余的元素
            if ((maxNumElems & 63) != 0) {
                bitmapLength++;
            }

            // 初始化位图，将所有位设置为0，表示所有元素都可用
            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;
            }
        }
        // 将当前内存池实例添加到池列表中，以便可以被其他部分使用
        addToPool();
    }



    /**
     * 分配内存块。
     *
     * @return 内存块的句柄，如果无法分配则返回-1。
     */
    long allocate() {
        // 如果元素大小为0，直接返回句柄0
        if (elemSize == 0) {
            return toHandle(0);
        }

        // 如果没有可用的元素或者不允许破坏，则返回-1表示分配失败
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        // 获取下一个可用的位索引
        final int bitmapIdx = getNextAvail();
        // 计算位映射的索引和位偏移
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        // 断言所选的位应该是未被使用的（即为0）
        assert (bitmap[q] >>> r & 1) == 0;
        // 将该位设置为已使用
        bitmap[q] |= 1L << r;

        // 如果这是最后一个可用的元素，则从池中移除当前对象
        if (--numAvail == 0) {
            removeFromPool();
        }

        // 返回内存块的句柄
        return toHandle(bitmapIdx);
    }



    /**
     * 释放指定索引的元素。
     *
     * @param bitmapIdx 要释放的元素的索引。
     * @return 如果成功释放元素，则返回true；如果元素已经是释放状态，则返回false。
     */
    boolean free(int bitmapIdx) {

        // 如果元素大小为0，表示所有元素都是可用的，因此直接返回true。
        if (elemSize == 0) {
            return true;
        }

        // 计算bitmap索引和位索引。
        int q = bitmapIdx >>> 6;  // 计算bitmap索引
        int r = bitmapIdx & 63;   // 计算位索引

        // 断言指定索引处的位应该是1，即该元素应该被占用。
        assert (bitmap[q] >>> r & 1) != 0;

        // 翻转指定索引处的位，表示该元素现在是可用的。
        bitmap[q] ^= 1L << r;

        // 更新下一个可用元素的索引。
        setNextAvail(bitmapIdx);

        // 如果是第一个被释放的元素，则需要将其添加到空闲池中。
        if (numAvail++ == 0) {
            addToPool();
            return true;
        }

        // 如果不是所有元素都已被释放，则返回true。
        if (numAvail != maxNumElems) {
            return true;
        } else {
            // 如果是所有元素都已被释放，进一步判断是否是唯一的子页面。
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

    /**
     * 查找下一个可用的位索引。
     * 该方法通过遍历位图数组，寻找第一个非全1的块，然后在该块中查找第一个可用的位。
     * 如果所有块都是全1的，表示没有可用的位，返回-1。
     *
     * @return 返回下一个可用的位索引，如果所有位都已被占用，则返回-1。
     */
    private int findNextAvail() {
        // 访问位图数组
        final long[] bitmap = this.bitmap;
        // 获取位图数组的长度
        final int bitmapLength = this.bitmapLength;
        // 遍历位图数组的每个元素
        for (int i = 0; i < bitmapLength; i++) {
            // 获取当前块的位图
            long bits = bitmap[i];
            // 检查当前块中是否有可用的位
            // 如果当前块不是全1的，则存在可用的位
            if (~bits != 0) {
                // 调用辅助方法在当前块中查找并返回第一个可用的位索引
                return findNextAvail0(i, bits);
            }
        }
        // 如果所有块都是全1的，表示没有可用的位，返回-1
        return -1;
    }


    /**
     * 查找下一个可用的0位，并返回其对应的索引值。
     * 该方法用于处理一个长整型bits变量，其中的每一位代表一个元素的状态（0表示可用，1表示已占用）。
     * 从给定的索引i开始，向后查找第一个可用的元素索引。
     *
     * @param i        开始查找的索引
     * @param bits     表示元素状态的长整型变量，每一位对应一个元素
     * @return 返回第一个可用元素的索引，如果不存在则返回-1
     */
    private int findNextAvail0(int i, long bits) {
        // 获取最大元素数量
        final int maxNumElems = this.maxNumElems;
        // 计算起始值，i左移6位（因为每6位代表一个元素）
        final int baseVal = i << 6;

        // 遍历每一位，查找第一个为0的位
        for (int j = 0; j < 64; j++) {
            // 如果当前位为0
            if ((bits & 1) == 0) {
                // 计算对应的元素索引
                int val = baseVal | j;
                // 如果索引小于最大元素数量，则返回该索引
                if (val < maxNumElems) {
                    return val;
                } else {
                    // 如果索引超出最大元素数量，说明后续不会有可用元素，跳出循环
                    break;
                }
            }
            // 将bits右移一位，继续检查下一位
            bits >>>= 1;
        }
        // 如果没有找到可用元素，返回-1
        return -1;
    }


    /**
     * 将bitmap索引和memoryMap索引组合成一个长整型值。
     * 这个方法的目的是为了通过位运算将bitmap索引和memoryMap索引结合在一起，形成一个唯一的标识符，
     * 用于内部数据结构的处理或存储。高位是固定的特殊标识，中间32位是bitmap索引，低位是memoryMap索引。
     *
     * @param bitmapIdx bitmap索引，用于标识特定的bitmap位。
     * @return 组合了bitmapIdx和memoryMapIdx的长整型值。
     */
    private long toHandle(int bitmapIdx) {
        // 使用位运算和按位或操作将固定的高1位、bitmap索引的中间32位和memoryMap索引的低32位组合成一个长整型值。
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }


}
