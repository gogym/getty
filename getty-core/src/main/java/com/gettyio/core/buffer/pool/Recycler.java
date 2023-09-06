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


import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于线程本地堆栈的可回收对象池。
 *
 * @param <T> 池化对象的类型
 */
public abstract class Recycler<T> {

    /**
     * id生成，确保增量是原子性的
     */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(Integer.MIN_VALUE);
    /**
     * 线程id
     */
    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();
    /**
     * 默认对象池容量
     */
    private static final int DEFAULT_MAX_CAPACITY;
    /**
     * 初始化容量
     */
    private static final int INITIAL_CAPACITY;

    static {
        // 为不同的对象类型使用不同的最大容量，应该随着生产环境调整。暂时使用默认即可
        DEFAULT_MAX_CAPACITY = 262144;
        INITIAL_CAPACITY = 256;
    }

    /**
     * 最大容量
     */
    private final int maxCapacity;
    /**
     * 构造方法
     */
    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY);
    }

    protected Recycler(int maxCapacity) {
        this.maxCapacity = Math.max(0, maxCapacity);
    }

    /**
     * 创建一个ThreadLocal
     */
    private final ThreadLocal<Stack<T>> threadLocal = new ThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacity);
        }
    };

    /**
     * 获取对象
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public final T get() {
        Stack<T> stack = threadLocal.get();
        DefaultHandle<T> handle = stack.pop();
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return (T) handle.value;
    }


    /**
     * 新对象
     *
     * @param handle
     * @return
     */
    protected abstract T newObject(Handle<T> handle);


    /**
     * 处理器
     *
     * @param <T>
     */
    public interface Handle<T> {
        void recycle(T object);
    }

    /**
     * 默认处理器
     *
     * @param <T>
     */
    static final class DefaultHandle<T> implements Handle<T> {
        private int lastRecycledId;
        private int recycleId;

        private Stack<?> stack;
        private Object value;

        DefaultHandle(Stack<?> stack) {
            this.stack = stack;
        }

        @Override
        public void recycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            Thread thread = Thread.currentThread();
            if (thread == stack.thread) {
                stack.push(this);
                return;
            }
            Map<Stack<?>, WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            WeakOrderQueue queue = delayedRecycled.get(stack);
            if (queue == null) {
                delayedRecycled.put(stack, queue = new WeakOrderQueue(stack, thread));
            }
            queue.add(this);
        }
    }

    /**
     * 延迟回收站
     */
    private static final ThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED = new ThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
        @Override
        protected Map<Stack<?>, WeakOrderQueue> initialValue() {
            return new WeakHashMap<>();
        }
    };


    /**
     * 一个只对可见性做出适度保证的队列:但不能绝对保证看到任何东西，因此保持队列的维护成本低
     */
    private static final class WeakOrderQueue {
        /**
         * 链大小
         */
        private static final int LINK_CAPACITY = 16;

        /**
         * 让Link为intrinsic扩展AtomicInteger。链接本身将被用作writerIndex。
         */
        private static final class Link extends AtomicInteger {
            private final DefaultHandle<?>[] elements = new DefaultHandle[LINK_CAPACITY];
            /**
             * 当前读取下标
             */
            private int readIndex;
            /**
             * 下一个
             */
            private Link next;
        }

        /**
         * 数据项链，头、尾
         */
        private Link head, tail;
        /**
         * 指向同一堆栈的另一个延迟项队列的指针
         */
        private WeakOrderQueue next;
        private final WeakReference<Thread> owner;
        private final int id = ID_GENERATOR.getAndIncrement();

        WeakOrderQueue(Stack<?> stack, Thread thread) {
            head = tail = new Link();
            owner = new WeakReference<>(thread);
            synchronized (stack) {
                //初始化，当前和下一个都是当前对象
                next = stack.head;
                stack.head = this;
            }
        }

        /**
         * 添加handle
         *
         * @param handle
         */
        void add(DefaultHandle<?> handle) {
            handle.lastRecycledId = id;

            Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.get()) == LINK_CAPACITY) {
                this.tail = tail = tail.next = new Link();
                writeIndex = tail.get();
            }
            tail.elements[writeIndex] = handle;
            handle.stack = null;
            //我们延迟设置堆栈为空，以确保在unnull它之前出现在拥有线程;
            //这也意味着如果看到索引被更新，则保证队列中元素的可见性
            tail.lazySet(writeIndex + 1);
        }

        boolean hasFinalData() {
            return tail.readIndex != tail.get();
        }

        /**
         * 将尽可能多的项从队列转移到堆栈，如果有的话返回true
         */
        @SuppressWarnings("rawtypes")
        boolean transfer(Stack<?> dst) {

            Link head = this.head;
            if (head == null) {
                return false;
            }

            if (head.readIndex == LINK_CAPACITY) {
                if (head.next == null) {
                    return false;
                }
                this.head = head = head.next;
            }

            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            final int srcSize = srcEnd - srcStart;
            if (srcSize == 0) {
                return false;
            }

            final int dstSize = dst.size;
            final int expectedCapacity = dstSize + srcSize;

            if (expectedCapacity > dst.elements.length) {
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = Math.min(srcStart + actualCapacity - dstSize, srcEnd);
            }

            if (srcStart != srcEnd) {
                final DefaultHandle[] srcElems = head.elements;
                final DefaultHandle[] dstElems = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    DefaultHandle element = srcElems[i];
                    if (element.recycleId == 0) {
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    element.stack = dst;
                    dstElems[newDstSize++] = element;
                    srcElems[i] = null;
                }
                dst.size = newDstSize;

                if (srcEnd == LINK_CAPACITY && head.next != null) {
                    this.head = head.next;
                }

                head.readIndex = srcEnd;
                return true;
            } else {
                // 目标堆栈已经满了。
                return false;
            }
        }
    }

    /**
     * 堆栈
     *
     * @param <T>
     */
    static final class Stack<T> {

        final Recycler<T> parent;
        final Thread thread;
        private DefaultHandle<?>[] elements;
        private final int maxCapacity;
        private int size;

        /**
         * 每个线程都有一个队列，每次除堆栈所有者外的新线程回收时，这个队列只被追加一次:
         * 当堆栈中的项用完时，迭代这个集合，以查找那些可以重用的项。这允许我们在回收所有项目的同时产生最小的线程同步。
         */
        private volatile WeakOrderQueue head;
        private WeakOrderQueue cursor, prev;

        /**
         * 构造
         *
         * @param parent      回收站
         * @param thread      线程
         * @param maxCapacity 最大堆栈容量
         */
        Stack(Recycler<T> parent, Thread thread, int maxCapacity) {
            this.parent = parent;
            this.thread = thread;
            this.maxCapacity = maxCapacity;
            elements = new DefaultHandle[Math.min(INITIAL_CAPACITY, maxCapacity)];
        }

        /**
         * 追加容量
         *
         * @param expectedCapacity 需要追加的容量
         * @return
         */
        int increaseCapacity(int expectedCapacity) {
            int newCapacity = elements.length;
            int maxCapacity = this.maxCapacity;
            do {
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);

            newCapacity = Math.min(newCapacity, maxCapacity);
            if (newCapacity != elements.length) {
                elements = Arrays.copyOf(elements, newCapacity);
            }

            return newCapacity;
        }

        /**
         * 出栈
         *
         * @return
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        DefaultHandle<T> pop() {
            int size = this.size;
            if (size == 0) {
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            size--;
            DefaultHandle ret = elements[size];
            if (ret.lastRecycledId != ret.recycleId) {
                throw new IllegalStateException("recycled multiple times");
            }
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            this.size = size;
            return ret;
        }


        boolean scavenge() {
            // 继续现有的清除(如果有)
            if (scavengeSome()) {
                return true;
            }

            // 重置我们的清除光标
            prev = null;
            cursor = head;
            return false;
        }

        /**
         * 清理
         *
         * @return
         */
        boolean scavengeSome() {
            //当前游标
            WeakOrderQueue cursor = this.cursor;
            if (cursor == null) {
                cursor = head;
                if (cursor == null) {
                    return false;
                }
            }

            boolean success = false;
            WeakOrderQueue prev = this.prev;
            do {
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }

                WeakOrderQueue next = cursor.next;
                if (cursor.owner.get() == null) {
                    //如果与队列关联的线程已经消失，在执行volatile read操作确认没有数据需要收集后，解除它的链接。我们从不断开第一个队列的链接，因为我们不想在更新头部时进行同步。
                    if (cursor.hasFinalData()) {
                        for (; ; ) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;
                            }
                        }
                    }
                    if (prev != null) {
                        prev.next = next;
                    }
                } else {
                    prev = cursor;
                }
                cursor = next;
            } while (cursor != null && !success);

            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        /**
         * 入栈
         *
         * @param item
         */
        void push(DefaultHandle<?> item) {
            if ((item.recycleId | item.lastRecycledId) != 0) {
                throw new IllegalStateException("recycled already");
            }
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;

            int size = this.size;
            if (size >= maxCapacity) {
                // 击中最大容量-丢弃可能最年轻的对象。
                return;
            }
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, Math.min(size << 1, maxCapacity));
            }

            elements[size] = item;
            this.size = size + 1;
        }


        /**
         * 创建默认堆栈
         *
         * @return
         */
        DefaultHandle<T> newHandle() {
            return new DefaultHandle<T>(this);
        }
    }
}
