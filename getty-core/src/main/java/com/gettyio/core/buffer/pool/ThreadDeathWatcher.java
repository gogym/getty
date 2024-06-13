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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定期检查线程是否处于活动状态，并在线程死亡时运行任务。
 * <p>
 * 这个线程启动一个守护线程来检查被监视的线程的状态，并调用它们相关的{@link Runnable}。
 * 当没有线程要监视时(即所有线程都死了)，守护线程将终止自己，当添加新的监视时，一个新的守护线程将再次启动。
 * </p>
 */
public final class ThreadDeathWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ThreadDeathWatcher.class);
    static final ThreadFactory threadFactory;

    // 使用MPMC队列，因为我们可能会从多个线程中检查isEmpty()，这可能不会被允许并发执行，这取决于它在MPSC队列中的实现。
    private static final Queue<Entry> pendingEntries = new ConcurrentLinkedQueue<Entry>();
    private static final Watcher watcher = new Watcher();
    private static final AtomicBoolean started = new AtomicBoolean();

    static {
        // 因为ThreadDeathWatcher是单例的，提交给它的任务可以来自任意的线程，这可以触发从任意线程组创建线程;由于这个原因，线程工厂不能粘着它的线程组
        threadFactory = Executors.defaultThreadFactory();
    }

    private ThreadDeathWatcher() {
    }

    /**
     * 当指定的{@code线程}死亡时，调度指定的{@code任务}运行。
     *
     * @param thread the {@link Thread} to watch
     * @param task   the {@link Runnable} to run when the {@code thread} dies
     * @throws IllegalArgumentException if the specified {@code thread} is not alive
     */
    public static void watch(Thread thread, Runnable task) {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (!thread.isAlive()) {
            throw new IllegalArgumentException("thread must be alive.");
        }

        schedule(thread, task, true);
    }

    /**
     * 通过{@link #watch(Thread, Runnable)}取消计划的任务。
     */
    public static void unwatch(Thread thread, Runnable task) {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        if (task == null) {
            throw new NullPointerException("task");
        }

        schedule(thread, task, false);
    }

    /**
     * 定义一个方法用于安排任务的执行。
     * 此方法用于将一个新的任务加入到待执行的任务队列中，并根据情况启动一个监控线程。
     * 如果监控线程尚未启动，则会通过原子操作启动它。
     *
     * @param thread 用于执行任务的线程。
     * @param task 待执行的任务。
     * @param isWatch 指示是否为监控任务，用于决定是否启动监控线程。
     */
    private static void schedule(Thread thread, Runnable task, boolean isWatch) {
        // 将新的任务加入到待执行的任务队列中
        pendingEntries.add(new Entry(thread, task, isWatch));

        // 如果监控线程尚未启动，并且通过compareAndSet操作成功将started标记为true，则启动监控线程
        if (started.compareAndSet(false, true)) {
            // 使用线程工厂创建一个新的监控线程
            Thread watcherThread = threadFactory.newThread(watcher);
            // 启动监控线程
            watcherThread.start();
        }
    }



    /**
     * Watcher类是一个内部私有静态类，实现了Runnable接口，用于监控和处理特定的Entry对象。
     * 它的主要职责是周期性地从一个队列中获取Entry对象，根据对象的状态决定是添加到监控列表还是从列表中移除，
     * 并且对不再关联活着的线程的Entry对象执行相应的任务。
     */
    private static final class Watcher implements Runnable {
        // 存储正在监控的Entry对象的列表
        private final List<Entry> watchees = new ArrayList<Entry>();

        /**
         * Watcher的主要运行循环。
         * 它不断地从pendingEntries队列中获取Entry对象，根据对象的isWatch标志决定是否添加到watchees列表中，
         * 然后检查watchees列表中的每个对象，如果关联的线程不再存活，则执行任务并从列表中移除。
         */
        @Override
        public void run() {
            for (; ; ) {
                fetchWatchees();
                notifyWatchees();
                fetchWatchees();
                notifyWatchees();

                try {
                    // 每秒检查一次
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    // 忽略中断异常
                }

                // 如果watchees和pendingEntries都为空，尝试停止Watcher
                if (watchees.isEmpty() && pendingEntries.isEmpty()) {
                    boolean stopped = started.compareAndSet(true, false);
                    assert stopped;

                    // 如果pendingEntries仍然为空，结束循环
                    if (pendingEntries.isEmpty()) {
                        break;
                    }

                    // 如果无法重新启动Watcher，结束循环
                    if (!started.compareAndSet(false, true)) {
                        break;
                    }
                }
            }
        }

        /**
         * 从pendingEntries队列中获取Entry对象，并根据isWatch标志决定是否添加到watchees列表中或从列表中移除。
         */
        private void fetchWatchees() {
            for (; ; ) {
                Entry e = pendingEntries.poll();
                if (e == null) {
                    break;
                }

                if (e.isWatch) {
                    watchees.add(e);
                } else {
                    watchees.remove(e);
                }
            }
        }

        /**
         * 遍历watchees列表，对不再关联活着的线程的Entry对象执行任务，并从列表中移除。
         */
        private void notifyWatchees() {
            List<Entry> watchees = this.watchees;
            for (int i = 0; i < watchees.size(); ) {
                Entry e = watchees.get(i);
                if (!e.thread.isAlive()) {
                    watchees.remove(i);
                    try {
                        e.task.run();
                    } catch (Throwable t) {
                        logger.warn("Thread death watcher task raised an exception:", t);
                    } finally {
                        cleanThreadLocals(e.thread);
                    }
                } else {
                    i++;
                }
            }
        }
    }


    /**
     * 用于存储线程和任务的关联实体类，可以标识某个线程正在执行或即将执行的任务。
     * 此类还区分了是否为监控任务，以便进行不同的管理。
     */
    private static final class Entry {
        /**
         * 关联的线程。
         */
        final Thread thread;
        /**
         * 关联的任务。
         */
        final Runnable task;
        /**
         * 标志位，表示该条目是否为监控任务。
         */
        final boolean isWatch;

        /**
         * 构造函数，初始化Entry对象。
         *
         * @param thread 关联的线程。
         * @param task 关联的任务。
         * @param isWatch 标志位，表示该条目是否为监控任务。
         */
        Entry(Thread thread, Runnable task, boolean isWatch) {
            this.thread = thread;
            this.task = task;
            this.isWatch = isWatch;
        }

        /**
         * 重写hashCode方法，根据线程和任务的hashCode计算Entry的hashCode。
         * 这样可以将Entry对象放入基于hash的集合中，如HashMap。
         *
         * @return Entry的hashCode。
         */
        @Override
        public int hashCode() {
            return thread.hashCode() ^ task.hashCode();
        }

        /**
         * 重写equals方法，判断两个Entry对象是否相等。
         * 两个Entry对象相等的条件是它们关联的线程和任务相同。
         *
         * @param obj 要比较的对象。
         * @return 如果两个对象相等返回true，否则返回false。
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof Entry)) {
                return false;
            }

            Entry that = (Entry) obj;
            return thread == that.thread && task == that.task;
        }
    }

    /**
     * 清理指定线程的ThreadLocal变量。
     *
     * ThreadLocal为线程局部变量，提供线程之间的隔离。然而，如果不适当管理，ThreadLocal可能会导致内存泄露。
     * 本方法旨在显式清理指定线程的ThreadLocal变量，以避免潜在的内存泄露问题。
     *
     * @param thread 需要被清理ThreadLocal变量的线程。
     */
    private static void cleanThreadLocals(Thread thread) {
        try {
            // 访问Thread类中私有的threadLocals字段，用于存储线程局部变量。
            Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            Object threadLocalTable = threadLocalsField.get(thread);
            // 如果threadLocalTable为空，则无需进行清理。
            if (threadLocalTable == null) {
                return;
            }

            // 获取ThreadLocalMap类，这是ThreadLocal的内部类，用于实际存储线程局部变量。
            Class threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            // 访问ThreadLocalMap中的table字段，这是一个Entry数组，存储着线程局部变量。
            Field tableField = threadLocalMapClass.getDeclaredField("table");
            tableField.setAccessible(true);
            Object table = tableField.get(threadLocalTable);

            // 获取Reference类中的referent字段，这个字段引用着ThreadLocalMap中的键对象。
            Field referentField = Reference.class.getDeclaredField("referent");
            referentField.setAccessible(true);

            // 遍历table数组，清理每个非空的Entry。
            for (int i = 0; i < Array.getLength(table); i++) {
                Object entry = Array.get(table, i);
                // 如果entry不为空，则尝试清理对应的ThreadLocal变量。
                if (entry != null) {
                    ThreadLocal threadLocal = (ThreadLocal) referentField.get(entry);
                    threadLocal.remove();
                }
            }
        } catch (Exception e) {
            // 如果在清理过程中发生异常，抛出IllegalStateException。
            throw new IllegalStateException(e);
        }
    }


}
