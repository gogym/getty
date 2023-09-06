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

    private static void schedule(Thread thread, Runnable task, boolean isWatch) {
        pendingEntries.add(new Entry(thread, task, isWatch));

        if (started.compareAndSet(false, true)) {
            Thread watcherThread = threadFactory.newThread(watcher);
            watcherThread.start();
        }
    }


    private static final class Watcher implements Runnable {

        private final List<Entry> watchees = new ArrayList<Entry>();

        @Override
        public void run() {
            for (; ; ) {
                fetchWatchees();
                notifyWatchees();

                // 再试一次，以防notifyWatchees()触发watch()或unwatch()。
                fetchWatchees();
                notifyWatchees();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    // 忽略中断;在所有任务运行完毕之前不要终止。
                }

                if (watchees.isEmpty() && pendingEntries.isEmpty()) {

                    //将当前工作线程标记为已停止。以下CAS必须总是成功且必须是无竞争的，因为在同一时间只能运行一个监视线程。
                    boolean stopped = started.compareAndSet(true, false);
                    assert stopped;

                    // 在我们进行CAS时，检查watch()是否添加了待处理的条目。
                    if (pendingEntries.isEmpty()) {
                        // A) watch() was not invoked and thus there's nothing to handle
                        //    -> safe to terminate because there's nothing left to do
                        // B) a new watcher thread started and handled them all
                        //    -> safe to terminate the new watcher thread will take care the rest
                        break;
                    }

                    // 还有一些待定条目，是由watch()添加的
                    if (!started.compareAndSet(false, true)) {
                        // watch() started a new watcher thread and set 'started' to true.
                        // -> terminate this thread so that the new watcher reads from pendingEntries exclusively.
                        break;
                    }

                    // watch() added an entry, but this worker was faster to set 'started' to true.
                    // i.e. a new watcher thread was not started
                    // -> keep this thread alive to handle the newly added entries.
                }
            }
        }

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

    private static final class Entry {
        final Thread thread;
        final Runnable task;
        final boolean isWatch;

        Entry(Thread thread, Runnable task, boolean isWatch) {
            this.thread = thread;
            this.task = task;
            this.isWatch = isWatch;
        }

        @Override
        public int hashCode() {
            return thread.hashCode() ^ task.hashCode();
        }

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

    private static void cleanThreadLocals(Thread thread) {
        try {
            // Get a reference to the thread locals table of the current thread
            //Thread thread = Thread.currentThread();
            Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            Object threadLocalTable = threadLocalsField.get(thread);
            if (threadLocalTable == null) {
                return;
            }
            // Get a reference to the array holding the thread local variables inside the
            // ThreadLocalMap of the current thread
            Class threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            Field tableField = threadLocalMapClass.getDeclaredField("table");
            tableField.setAccessible(true);
            Object table = tableField.get(threadLocalTable);

            // The key to the ThreadLocalMap is a WeakReference object. The referent field of this object
            // is a reference to the actual ThreadLocal variable
            Field referentField = Reference.class.getDeclaredField("referent");
            referentField.setAccessible(true);

            for (int i = 0; i < Array.getLength(table); i++) {
                // Each entry in the table array of ThreadLocalMap is an Entry object
                // representing the thread local reference and its value
                Object entry = Array.get(table, i);
                if (entry != null) {
                    // Get a reference to the thread local object and remove it from the table
                    ThreadLocal threadLocal = (ThreadLocal) referentField.get(entry);
                    threadLocal.remove();
                }
            }
        } catch (Exception e) {
            // We will tolerate an exception here and just log it
            throw new IllegalStateException(e);
        }
    }

}
