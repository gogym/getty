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

    /**
     * 构造函数初始化Recycler对象。
     *
     * @param maxCapacity Recycler的最大容量。如果传入的值小于0，则默认设置为0。
     *                    这个参数用于控制Recycler能够管理的对象数量，以避免过度回收带来的性能问题。
     */
    protected Recycler(int maxCapacity) {
        // 使用Math.max确保maxCapacity至少为0，避免负数造成的逻辑错误。
        this.maxCapacity = Math.max(0, maxCapacity);
    }


    /**
     * 使用ThreadLocal维护一个线程安全的Stack实例。
     * 这样每个线程都可以拥有自己的Stack实例，避免了多线程环境下的竞争条件，提高了效率。
     * 初始时，每个线程的Stack实例都会通过initialValue()方法进行初始化。
     */
    private final ThreadLocal<Stack<T>> threadLocal = new ThreadLocal<Stack<T>>() {
        /**
         * 提供ThreadLocal的初始值。
         * 这里创建一个新的Stack实例，并将其与当前线程、Recycler实例和最大容量关联起来。
         * @return 新创建的Stack实例，用于当前线程。
         */
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacity);
        }
    };



    /**
     * 从线程局部变量中获取特定类型的对象。
     * 此方法首先尝试从线程局部栈中弹出一个句柄，如果栈为空或句柄为null，则创建一个新的对象并将其句柄压入栈中。
     * 最后，将句柄的值强制转换为泛型类型T并返回。
     *
     * @return 栈中句柄的值，或者是新创建对象的句柄值。
     */
    @SuppressWarnings("unchecked")
    public final T get() {
        // 从线程局部变量中获取特定于线程的栈
        Stack<T> stack = threadLocal.get();
        // 尝试从栈中弹出一个句柄
        DefaultHandle<T> handle = stack.pop();
        // 如果栈为空或句柄为null，创建一个新的句柄
        if (handle == null) {
            handle = stack.newHandle();
            // 通过调用newObject方法创建一个新的对象，并将其赋值给句柄
            handle.value = newObject(handle);
        }
        // 将句柄的值强制转换为泛型类型T并返回
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
     * 默认的处理类，实现了Handle接口。
     * 该类用于管理对象的回收，特别是当对象需要被返回到一个共享的池中时。
     * 使用栈来管理回收的实例，以确保它们可以在适当的线程上下文中被重新使用。
     *
     * @param <T> 处理器支持的泛型类型。
     */
    static final class DefaultHandle<T> implements Handle<T> {
        // 上一次回收时的ID，用于管理回收的序列。
        private int lastRecycledId;
        // 当前的回收ID，用于管理回收的序列。
        private int recycleId;

        // 用于存储和管理DefaultHandle实例的栈。
        private Stack<?> stack;
        // 与当前DefaultHandle实例关联的值。
        private Object value;

        /**
         * 构造函数初始化DefaultHandle。
         *
         * @param stack 用于管理DefaultHandle实例的栈。
         */
        DefaultHandle(Stack<?> stack) {
            this.stack = stack;
        }

        /**
         * 回收当前的处理实例。
         * 确保回收的对象与当前处理实例关联的对象相同，否则抛出异常。
         * 如果当前线程与栈关联的线程相同，则直接将实例推入栈中。
         * 否则，将实例添加到延迟回收队列中，以在正确的线程上下文中进行回收。
         *
         * @param object 要回收的对象，必须与当前处理实例关联的对象相同。
         */
        @Override
        public void recycle(Object object) {
            // 确保回收的对象属于当前处理实例。
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            // 获取当前线程，用于比较是否与栈关联的线程相同。
            Thread thread = Thread.currentThread();
            // 如果当前线程与栈关联的线程相同，则直接将实例推入栈中。
            if (thread == stack.thread) {
                stack.push(this);
                return;
            }
            // 获取延迟回收队列，用于管理在错误的线程上下文中回收的实例。
            Map<Stack<?>, WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            // 从延迟回收队列中获取与当前栈关联的队列，如果不存在则创建新的队列。
            WeakOrderQueue queue = delayedRecycled.get(stack);
            if (queue == null) {
                delayedRecycled.put(stack, queue = new WeakOrderQueue(stack, thread));
            }
            // 将当前处理实例添加到延迟回收队列中。
            queue.add(this);
        }
    }



    /**
     * 一个线程局部变量，用于存储被延迟回收的Stack对象映射到WeakOrderQueue。
     * 使用WeakHashMap以便在不再有引用时能够自动回收这些对象，避免内存泄漏。
     * WeakOrderQueue用于维护一个按回收顺序排列的队列，以便于按顺序处理回收的Stack对象。
     */
    private static final ThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED = new ThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
        @Override
        protected Map<Stack<?>, WeakOrderQueue> initialValue() {
            // 初始化一个WeakHashMap来存储Stack对象和对应的WeakOrderQueue。
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
         * 将当前链栈中的元素转移至目标堆栈中。
         * 转移过程中，会检查源链栈是否有元素可供转移，以及目标堆栈是否有足够的容量接收元素。
         * 如果转移成功，会更新源链栈和目标堆栈的状态；如果无法转移，将返回false。
         *
         * @param dst 目标堆栈，用于接收来自当前链栈的元素。
         * @return 如果元素成功转移，则返回true；否则返回false。
         */
        @SuppressWarnings("rawtypes")
        boolean transfer(Stack<?> dst) {
            // 获取当前链栈的头部链接。
            Link head = this.head;
            // 如果头部链接为空，说明链栈为空，无法进行转移操作。
            if (head == null) {
                return false;
            }

            // 如果头部链接的读索引已经达到最大值，需要检查是否存在下一个链接。
            if (head.readIndex == LINK_CAPACITY) {
                // 如果没有下一个链接，说明无法进行转移操作。
                if (head.next == null) {
                    return false;
                }
                // 如果存在下一个链接，更新头部链接为下一个链接。
                this.head = head = head.next;
            }

            // 获取源链栈的起始读索引和当前读索引位置。
            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            // 计算源链栈中待转移的元素数量。
            final int srcSize = srcEnd - srcStart;
            // 如果源链栈中没有元素可供转移，返回false。
            if (srcSize == 0) {
                return false;
            }

            // 获取目标堆栈的当前大小。
            final int dstSize = dst.size;
            // 预计转移后目标堆栈的大小。
            final int expectedCapacity = dstSize + srcSize;

            // 检查目标堆栈是否有足够的容量接收源链栈的元素。
            if (expectedCapacity > dst.elements.length) {
                // 计算目标堆栈需要增加的容量，并调整源链栈元素的转移数量。
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = Math.min(srcStart + actualCapacity - dstSize, srcEnd);
            }

            // 如果源链栈中存在元素可供转移，进行实际的转移操作。
            if (srcStart != srcEnd) {
                // 获取源链栈和目标堆栈的元素数组。
                final DefaultHandle[] srcElems = head.elements;
                final DefaultHandle[] dstElems = dst.elements;
                // 初始化目标堆栈的新大小。
                int newDstSize = dstSize;
                // 遍历源链栈中的元素，进行转移。
                for (int i = srcStart; i < srcEnd; i++) {
                    DefaultHandle element = srcElems[i];
                    // 确保元素未被回收，或者已回收的元素再次使用时回收ID一致。
                    if (element.recycleId == 0) {
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    // 更新元素所属的堆栈为目标堆栈，并在目标堆栈中添加该元素。
                    element.stack = dst;
                    dstElems[newDstSize++] = element;
                    // 将源链栈中的元素置空，标记为已转移。
                    srcElems[i] = null;
                }
                // 更新目标堆栈的大小。
                dst.size = newDstSize;

                // 如果源链栈已转移完毕，更新链栈头部链接为下一个链接。
                if (srcEnd == LINK_CAPACITY && head.next != null) {
                    this.head = head.next;
                }

                // 更新源链栈头部链接的读索引位置。
                head.readIndex = srcEnd;
                // 转移成功，返回true。
                return true;
            } else {
                // 源链栈中无元素可供转移，返回false。
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
         * 从堆栈中弹出一个元素。
         * 如果堆栈为空，则尝试进行垃圾回收来获取新的元素。如果垃圾回收也无法提供元素，则返回null。
         * 此方法确保弹出的元素未被多次回收使用，如果检测到元素被多次回收，将抛出IllegalStateException。
         *
         * @return 弹出的元素，如果无法提供则返回null。
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        DefaultHandle<T> pop() {
            // 获取当前堆栈的大小
            int size = this.size;
            // 如果堆栈为空，尝试进行垃圾回收来获取新的元素
            if (size == 0) {
                // 如果垃圾回收无法提供新的元素，则返回null
                if (!scavenge()) {
                    return null;
                }
                // 更新获取后的堆栈大小
                size = this.size;
            }
            // 准备弹出元素前的大小减一操作
            size--;
            // 获取将要弹出的元素
            DefaultHandle ret = elements[size];
            // 检查元素是否被多次回收，如果是，则抛出异常
            if (ret.lastRecycledId != ret.recycleId) {
                throw new IllegalStateException("recycled multiple times");
            }
            // 重置元素的回收标识，以便它可以被重新使用
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            // 更新堆栈的大小
            this.size = size;
            // 返回弹出的元素
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
         * 尝试进行一些清理工作。
         * 这个方法遍历一个弱有序队列，试图将其中的数据转移到另一个地方。
         * 如果成功转移至少一个元素，则返回true；否则，返回false。
         *
         * @return 如果成功转移了至少一个元素，则返回true；否则返回false。
         */
        boolean scavengeSome() {
            // 使用当前的游标位置来开始清理工作
            WeakOrderQueue cursor = this.cursor;
            // 如果当前游标为空，则尝试使用头节点作为起始点
            if (cursor == null) {
                cursor = head;
                // 如果头节点也为空，说明没有元素需要清理，直接返回false
                if (cursor == null) {
                    return false;
                }
            }

            // 用于标记是否成功转移了元素
            boolean success = false;
            // 记录当前遍历的前一个节点，用于后续可能的链接断开操作
            WeakOrderQueue prev = this.prev;
            // 遍历队列，直到遍历结束或成功转移元素
            do {
                // 尝试转移当前节点的数据
                if (cursor.transfer(this)) {
                    // 如果转移成功，设置成功标记并跳出循环
                    success = true;
                    break;
                }

                // 获取下一个节点
                WeakOrderQueue next = cursor.next;
                // 检查当前节点的所有者是否为空，即该节点是否被废弃
                if (cursor.owner.get() == null) {
                    // 如果节点包含最终数据，尝试再次转移数据
                    // 如果与队列关联的线程已经消失，在执行volatile read操作确认没有数据需要收集后，解除它的链接。我们从不断开第一个队列的链接，因为我们不想在更新头部时进行同步。
                    if (cursor.hasFinalData()) {
                        // 不断尝试转移数据，直到成功或无法再转移
                        for (; ; ) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;
                            }
                        }
                    }
                    // 如果前一个节点不为空，断开当前节点和前一个节点的链接
                    if (prev != null) {
                        prev.next = next;
                    }
                } else {
                    // 如果当前节点的所有者不为空，更新前一个节点为当前节点
                    prev = cursor;
                }
                // 移动到下一个节点
                cursor = next;
            } while (cursor != null && !success);

            // 更新当前的前一个节点和游标位置
            this.prev = prev;
            this.cursor = cursor;
            // 返回是否成功转移了元素
            return success;
        }



        /**
         * 将给定的项推入堆栈中。
         * 此方法确保堆栈不接受已经回收的项，并在需要时扩展堆栈的容量。
         *
         * @param item 要推入堆栈的项。它必须是一个未被回收的项。
         * @throws IllegalStateException 如果项已经被回收，则抛出此异常。
         */
        void push(DefaultHandle<?> item) {
            // 检查item是否已经被回收，如果是，则抛出IllegalStateException异常
            if ((item.recycleId | item.lastRecycledId) != 0) {
                throw new IllegalStateException("recycled already");
            }
            // 将当前线程ID分配给item的回收ID和最后一个回收ID，标记它为未回收状态
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;

            // 获取堆栈当前的大小
            int size = this.size;
            // 如果堆栈已达到最大容量，则不再添加新元素，直接返回
            if (size >= maxCapacity) {
                return;
            }
            // 如果堆栈已满，则扩展堆栈容量
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, Math.min(size << 1, maxCapacity));
            }

            // 将项添加到堆栈中，并更新堆栈的大小
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
