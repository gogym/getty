/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.buffer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A pool of ByteBuffers kept under a given memory limit. This class is fairly specific to the needs of the producer. In
 * particular it has the following properties:
 * <ol>
 * <li>There is a special "poolable size" and buffers of this size are kept in a free list and recycled
 * <li>It is fair. That is all memory is given to the longest waiting thread until it has sufficient memory. This
 * prevents starvation or deadlock when a thread asks for a large chunk of memory and needs to block until multiple
 * buffers are deallocated.
 * </ol>
 */

/**
 * 内存池
 * 对于申请内存的线程来说是公平的，最先给等待时间最长的线程分配内存
 * 参考kafka结合netty内存池模式改造
 */
public final class ChunkPool {

    //内存池总内存空间大小
    private final long totalMemory;
    private final ReentrantLock lock;
    //空闲内存池，不会实际分配内存，只是做个标记
    //极小的
    private final int tinySize = 128;
    private final Deque<ByteBuffer> tinyFree;
    //小的
    private final int smallSize = 512;
    private final Deque<ByteBuffer> smallFree;
    //中等的
    private final int mediumSize = 1024;
    private final Deque<ByteBuffer> mediumFree;
    //大的
    private final int largeSize = 2048;
    private final Deque<ByteBuffer> largeFree;


    //等待队列标记
    private final Deque<Condition> waiters;
    //可用内存大小
    private long availableMemory;
    //时间控制
    private final Time time;
    /**
     * 是否堆内存
     */
    private boolean direct;

    /**
     * 创建内存池
     *
     * @param memory 内存池总大小
     * @param time   等待时间
     * @param direct 是否堆内存
     */
    public ChunkPool(long memory, Time time, boolean direct) {
        this.lock = new ReentrantLock();
        this.tinyFree = new ArrayDeque<>();
        this.smallFree = new ArrayDeque<>();
        this.mediumFree = new ArrayDeque<>();
        this.largeFree = new ArrayDeque<>();

        this.waiters = new ArrayDeque<>();
        this.totalMemory = memory;
        this.availableMemory = memory;
        this.time = time;
        this.direct = direct;
    }

    /**
     * 从空闲池分配给定大小的缓冲区。如果没有足够的内存和缓冲池，此方法将阻塞
     *
     * @param size             以字节为单位分配的缓冲区大小
     * @param maxTimeToBlockMs 缓冲区内存分配的最大阻塞时间(以毫秒为单位)
     * @return The buffer 返回缓冲区
     * @throws InterruptedException 异常
     * @throws TimeoutException     异常
     */
    public ByteBuffer allocate(int size, long maxTimeToBlockMs) throws InterruptedException, TimeoutException {
        if (size > this.totalMemory) {
            //分配内存大于总内存，抛出异常
            throw new IllegalArgumentException("Attempt to allocate " + size
                    + " bytes, but there is a hard limit of "
                    + this.totalMemory
                    + " on memory allocations.");
        }

        this.lock.lock();
        try {
            //检查是否有大小合适的缓冲池
            if (size <= tinySize) {
                size = tinySize;
                if (!this.tinyFree.isEmpty()) {
                    return this.tinyFree.pollFirst();
                }
            }
            if (size > tinySize && size <= smallSize) {
                size = smallSize;
                if (!this.smallFree.isEmpty()) {
                    return this.smallFree.pollFirst();
                }
            }
            if (size > smallSize && size <= mediumSize) {
                size = mediumSize;
                if (!this.mediumFree.isEmpty()) {
                    return this.mediumFree.pollFirst();
                }
            }

            if (size > mediumSize && size <= largeSize) {
                size = largeSize;
                if (!this.largeFree.isEmpty()) {
                    return this.largeFree.pollFirst();
                }
            }

//            if (size == poolableSize && !this.mediumFree.isEmpty()) {
//                return this.mediumFree.pollFirst();
//            }

            //检查总空闲内存是否满足分配需要
            int freeListSize = (this.tinySize * this.tinyFree.size()) + (this.smallSize * this.smallFree.size()) + (this.mediumSize * this.mediumFree.size()) + (this.largeSize * this.largeFree.size());
            if (this.availableMemory + freeListSize >= size) {
                //尝试释放空闲池
                freeUp(size);
                this.availableMemory -= size;
                lock.unlock();
                return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
            } else {
                // 我们的内存不足，将不得不阻塞
                int accumulated = 0;
                ByteBuffer buffer = null;
                Condition moreMemory = this.lock.newCondition();
                long remainingTimeToBlockNs = TimeUnit.MILLISECONDS.toNanos(maxTimeToBlockMs);
                // 循环，直到我们有一个缓冲区有足够的内存来分配
                this.waiters.addLast(moreMemory);
                while (accumulated < size) {
                    long startWaitNs = time.nanoseconds();
                    long timeNs;
                    boolean waitingTimeElapsed;
                    try {
                        waitingTimeElapsed = !moreMemory.await(remainingTimeToBlockNs, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        this.waiters.remove(moreMemory);
                        throw e;
                    } finally {
                        long endWaitNs = time.nanoseconds();
                        timeNs = Math.max(0L, endWaitNs - startWaitNs);
                    }
                    //如果等待时长超时，则抛出一个异常
                    if (waitingTimeElapsed) {
                        this.waiters.remove(moreMemory);
                        throw new TimeoutException("Failed to allocate memory within the configured max blocking time " + maxTimeToBlockMs + " ms.");
                    }

                    remainingTimeToBlockNs -= timeNs;
                    // 检查是否有空闲池可分配
                    if (accumulated == 0 && size <= this.tinySize && !this.tinyFree.isEmpty()) {
                        // 只需从空闲列表中获取一个缓冲区
                        buffer = this.tinyFree.pollFirst();
                        accumulated = size;
                    } else if (accumulated == 0 && size <= this.smallSize && !this.smallFree.isEmpty()) {
                        // 只需从空闲列表中获取一个缓冲区
                        buffer = this.smallFree.pollFirst();
                        accumulated = size;
                    } else if (accumulated == 0 && size <= this.mediumSize && !this.mediumFree.isEmpty()) {
                        // 只需从空闲列表中获取一个缓冲区
                        buffer = this.mediumFree.pollFirst();
                        accumulated = size;
                    } else if (accumulated == 0 && size <= this.largeSize && !this.largeFree.isEmpty()) {
                        // 只需从空闲列表中获取一个缓冲区
                        buffer = this.largeFree.pollFirst();
                        accumulated = size;
                    } else {
                        // 没有刚好合适的缓冲池，尝试释放
                        freeUp(size - accumulated);
                        int got = (int) Math.min(size - accumulated, this.availableMemory);
                        this.availableMemory -= got;
                        accumulated += got;
                    }
                }

                // 删除此线程让下一个线程通过，开始获取内存
                Condition removed = this.waiters.removeFirst();
                if (removed != moreMemory) {
                    throw new IllegalStateException("Wrong condition: this shouldn't happen.");
                }

                // 如果内存不够，就通知其他等待者,避免一直阻塞
                if (this.availableMemory > 0 || !this.mediumFree.isEmpty()) {
                    if (!this.waiters.isEmpty()) {
                        this.waiters.peekFirst().signal();
                    }
                }

                // 解锁并返回缓冲区
                lock.unlock();
                if (buffer == null) {
                    return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
                } else {
                    return buffer;
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    /**
     * 按需分配大小的缓冲区。如果没有足够的内存和缓冲池，此方法将阻塞
     *
     * @param size             以字节为单位分配的缓冲区大小
     * @param maxTimeToBlockMs 缓冲区内存分配的最大阻塞时间(以毫秒为单位)
     * @return The buffer 返回缓冲区
     * @throws InterruptedException 异常
     * @throws TimeoutException     异常
     */
    public ByteBuffer allocateBySize(int size, long maxTimeToBlockMs) throws InterruptedException, TimeoutException {
        if (size > this.totalMemory) {
            //分配内存大于总内存，抛出异常
            throw new IllegalArgumentException("Attempt to allocate " + size
                    + " bytes, but there is a hard limit of "
                    + this.totalMemory
                    + " on memory allocations.");
        }

        this.lock.lock();
        try {
            //检查总空闲内存是否满足分配需要
            int freeListSize = (this.tinySize * this.tinyFree.size()) + (this.smallSize * this.smallFree.size()) + (this.mediumSize * this.mediumFree.size()) + (this.largeSize * this.largeFree.size());
            if (this.availableMemory + freeListSize >= size) {
                //尝试释放空闲池
                freeUp(size);
                this.availableMemory -= size;
                lock.unlock();
                return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
            } else {
                // 我们的内存不足，将不得不阻塞
                int accumulated = 0;
                ByteBuffer buffer = null;
                Condition moreMemory = this.lock.newCondition();
                long remainingTimeToBlockNs = TimeUnit.MILLISECONDS.toNanos(maxTimeToBlockMs);
                // 循环，直到我们有一个缓冲区有足够的内存来分配
                this.waiters.addLast(moreMemory);
                while (accumulated < size) {
                    long startWaitNs = time.nanoseconds();
                    long timeNs;
                    boolean waitingTimeElapsed;
                    try {
                        waitingTimeElapsed = !moreMemory.await(remainingTimeToBlockNs, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        this.waiters.remove(moreMemory);
                        throw e;
                    } finally {
                        long endWaitNs = time.nanoseconds();
                        timeNs = Math.max(0L, endWaitNs - startWaitNs);
                    }
                    //如果等待时长超时，则抛出一个异常
                    if (waitingTimeElapsed) {
                        this.waiters.remove(moreMemory);
                        throw new TimeoutException("Failed to allocate memory within the configured max blocking time " + maxTimeToBlockMs + " ms.");
                    }

                    remainingTimeToBlockNs -= timeNs;

                    // 没有刚好合适的缓冲池，尝试释放
                    freeUp(size - accumulated);
                    int got = (int) Math.min(size - accumulated, this.availableMemory);
                    this.availableMemory -= got;
                    accumulated += got;

                }

                // 删除此线程让下一个线程通过，开始获取内存
                Condition removed = this.waiters.removeFirst();
                if (removed != moreMemory) {
                    throw new IllegalStateException("Wrong condition: this shouldn't happen.");
                }

                // 如果内存不够，就通知其他等待者,避免一直阻塞
                if (this.availableMemory > 0 || !this.mediumFree.isEmpty()) {
                    if (!this.waiters.isEmpty()) {
                        this.waiters.peekFirst().signal();
                    }
                }

                // 解锁并返回缓冲区
                lock.unlock();
                if (buffer == null) {
                    return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
                } else {
                    return buffer;
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 尝试通过释放池确保至少有被请求的内存字节数
     *
     * @param size 释放大小
     */
    private void freeUp(int size) {
        while (!(this.largeFree.isEmpty() && this.mediumFree.isEmpty() && this.smallFree.isEmpty() && this.tinyFree.isEmpty()) && this.availableMemory < size) {
            if (!this.largeFree.isEmpty()) {
                this.availableMemory += this.largeFree.pollLast().capacity();
                continue;
            }
            if (!this.mediumFree.isEmpty()) {
                this.availableMemory += this.mediumFree.pollLast().capacity();
                continue;
            }
            if (!this.smallFree.isEmpty()) {
                this.availableMemory += this.smallFree.pollLast().capacity();
                continue;
            }
            if (!this.tinyFree.isEmpty()) {
                this.availableMemory += this.tinyFree.pollLast().capacity();
                continue;
            }
        }
    }

    /**
     * 将缓冲区返回到池。如果它们是可占用的大小，则将它们添加到空闲列表中，否则仅标记
     *
     * @param buffer 要释放的缓冲区
     * @param size   要标记为释放的缓冲区的大小，注意这可能小于buffer.capacity,因为缓冲区可能在就地压缩期间重新分配自己
     */
    public void deallocate(ByteBuffer buffer, int size) {
        lock.lock();
        try {
            buffer.clear();
            if (size == tinySize && size == buffer.capacity()) {
                this.tinyFree.add(buffer);
            } else if (size == smallSize && size == buffer.capacity()) {
                this.smallFree.add(buffer);
            } else if (size == mediumSize && size == buffer.capacity()) {
                this.mediumFree.add(buffer);
            } else if (size == largeSize && size == buffer.capacity()) {
                this.largeFree.add(buffer);
            } else {
                this.availableMemory += size;
            }
            //通知等待内存的线程继续执行
            Condition moreMem = this.waiters.peekFirst();
            if (moreMem != null) {
                moreMem.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public void deallocate(ByteBuffer buffer) {
        if (null == buffer) {
            return;
        }
        deallocate(buffer, buffer.capacity());
    }

    /**
     * 未分配的和空闲列表中的总空闲内存
     *
     * @return long
     */
    public long availableMemory() {
        lock.lock();
        try {
            return this.availableMemory + (this.tinySize * this.tinyFree.size()) + (this.smallSize * this.smallFree.size()) + (this.mediumSize * this.mediumFree.size()) + (this.largeSize * this.largeFree.size());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取未分配的内存(不在空闲列表中或正在使用中)
     *
     * @return long
     */
    public long unallocatedMemory() {
        lock.lock();
        try {
            return this.availableMemory;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 等待内存时阻塞的线程数
     *
     * @return int
     */
    public int queued() {
        lock.lock();
        try {
            return this.waiters.size();
        } finally {
            lock.unlock();
        }
    }


    /**
     * 释放内存池
     */
    public void clear() {
        if (waiters != null) {
            waiters.clear();
        }
        if (mediumFree != null) {
            mediumFree.clear();
        }
    }


    /**
     * 此池管理的总内存
     *
     * @return long
     */
    public long totalMemory() {
        return this.totalMemory;
    }

    Deque<Condition> waiters() {
        return this.waiters;
    }


}
