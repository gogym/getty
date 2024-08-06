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
package com.gettyio.core.util.thread;


import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;


/**
 * ThreadPool.java
 *
 * @description:线程池类
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class ThreadPool {

    /**
     * 固定线程数目的线程池
     */
    public static final int FixedThread = 0;

    /**
     * 缓冲功能的线程池
     */
    public static final int CachedThread = 1;

    /**
     * 只支持一个线程的线程池
     */
    public static final int SingleThread = 2;

    /**
     * 常规线程池
     */
    private ExecutorService exec;

    /**
     * 可循环或延迟任务的线程池
     */
    private final ScheduledExecutorService scheduleExec;

    /**
     * 线程池大小
     */
    private final int corePoolSize;

    /**
     * ThreadPool构造函数
     *
     * @param type         线程池类型
     * @param corePoolSize 只对Fixed和Scheduled线程池起效
     */
    public ThreadPool(final int type, final int corePoolSize) {
        this.corePoolSize = corePoolSize;

        // 构造有定时功能的线程池
        scheduleExec = Executors.newScheduledThreadPool(corePoolSize);

        switch (type) {
            case FixedThread:
                // 构造一个固定线程数目的线程池
                exec = Executors.newFixedThreadPool(corePoolSize);
                break;
            case SingleThread:
                // 构造一个只支持一个线程的线程池,相当于newFixedThreadPool(1)
                exec = Executors.newSingleThreadExecutor();
                break;
            case CachedThread:
                // 构造一个缓冲功能的线程池
                exec = Executors.newCachedThreadPool();
                break;
        }
    }

    /**
     * 获取线程池size
     *
     * @return
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 在未来某个时间执行给定的命令
     * 该命令可能在新的线程、已入池的线程或者正调用的线程中执行，这由 Executor 实现决定。
     *
     * @param command 命令
     */
    public void execute(final Runnable command) {
        exec.execute(command);
    }

    /**
     * 在未来某个时间执行给定的命令链表
     * 该命令可能在新的线程、已入池的线程或者正调用的线程中执行，这由 Executor 实现决定。
     *
     * @param commands 命令链表
     */
    public void execute(final List<Runnable> commands) {
        for (Runnable command : commands) {
            exec.execute(command);
        }
    }

    /**
     * 待以前提交的任务执行完毕后关闭线程池
     * 启动一次顺序关闭，执行以前提交的任务，但不接受新任务。 如果已经关闭，则调用没有作用。
     */
    public void shutdown() {
        exec.shutdown();
        scheduleExec.shutdown();
    }

    /**
     * 试图停止所有正在执行的活动任务
     * 试图停止所有正在执行的活动任务，暂停处理正在等待的任务，并返回等待执行的任务列表。
     * 无法保证能够停止正在处理的活动执行任务，但是会尽力尝试。
     *
     * @return 等待执行的任务的列表
     */
    public List<Runnable> shutdownNow() {
        scheduleExec.shutdownNow();
        return exec.shutdownNow();
    }

    /**
     * 判断线程池是否已关闭
     *
     * @return true: 是  false: 否
     */
    public boolean isShutDown() {
        return exec.isShutdown();
    }

    /**
     * 关闭线程池后判断所有任务是否都已完成
     * 注意，除非首先调用 shutdown 或 shutdownNow，否则 isTerminated 永不为 true。
     *
     * @return true: 是 false: 否
     */
    public boolean isTerminated() {
        return exec.isTerminated();
    }

    /**
     * 请求关闭、发生超时或者当前线程中断
     * 无论哪一个首先发生之后，都将导致阻塞，直到所有任务完成执行。
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return true: 请求成功 false: 请求超时
     * @throws InterruptedException 异常
     */
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }

    /**
     * 提交一个Callable任务用于执行
     * 如果想立即阻塞任务的等待，则可以使用{@code result = exec.submit(aCallable).get();} 形式的构造。
     *
     * @param task 任务
     * @param <T>  泛型
     * @return 表示任务等待完成的Future, 该Future的{@code get}方法在成功完成时将会返回该任务的结果。
     */
    public <T> Future<T> submit(final Callable<T> task) {
        return exec.submit(task);
    }

    /**
     * 提交一个Runnable任务用于执行
     *
     * @param task   任务
     * @param result 返回的结果
     * @param <T>    泛型
     * @return 表示任务等待完成的Future, 该Future的{@code get}方法在成功完成时将会返回该任务的结果。
     */
    public <T> Future<T> submit(final Runnable task, final T result) {
        return exec.submit(task, result);
    }

    /**
     * 提交一个Runnable任务用于执行
     *
     * @param task 任务
     * @return 表示任务等待完成的Future, 该Future的{@code get}方法在成功完成时将会返回null结果。
     */
    public Future<?> submit(final Runnable task) {
        return exec.submit(task);
    }

    /**
     * 执行给定的任务
     * 当所有任务完成时，返回保持任务状态和结果的Future列表。 返回列表的所有元素的{@link Future#isDone}为{@code true}。
     * 注意，可以正常地或通过抛出异常来终止已完成任务。 如果正在进行此操作时修改了给定的 collection，则此方法的结果是不确定的。
     *
     * @param tasks 任务集合
     * @param <T>   泛型
     * @return 表示任务的 Future 列表，列表顺序与给定任务列表的迭代器所生成的顺序相同，每个任务都已完成。
     * @throws InterruptedException 如果等待时发生中断，在这种情况下取消尚未完成的任务。
     */
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return exec.invokeAll(tasks);
    }

    /**
     * 执行给定的任务
     * 当所有任务完成或超时期满时(无论哪个首先发生)，返回保持任务状态和结果的Future列表。 返回列表的所有元素的{@link Future#isDone}为
     * {@code true}。 一旦返回后，即取消尚未完成的任务。
     * 注意，可以正常地或通过抛出异常来终止已完成任务。 如果此操作正在进行时修改了给定的collection，则此方法的结果是不确定的。
     *
     * @param tasks   任务集合
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @param <T>     泛型
     * @return 表示任务的 Future 列表，列表顺序与给定任务列表的迭代器所生成的顺序相同。如果操作未超时，则已完成所有任务。如果确实超时了，则某些任务尚未完成。
     * @throws InterruptedException 如果等待时发生中断，在这种情况下取消尚未完成的任务
     */
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
        return exec.invokeAll(tasks, timeout, unit);
    }

    /**
     * 执行给定的任务
     * 如果某个任务已成功完成（也就是未抛出异常），则返回其结果。 一旦正常或异常返回后，则取消尚未完成的任务。
     * 如果此操作正在进行时修改了给定的collection，则此方法的结果是不确定的。
     *
     * @param tasks 任务集合
     * @param <T>   泛型
     * @return 某个任务返回的结果
     * @throws InterruptedException 如果等待时发生中断
     * @throws ExecutionException   如果没有任务成功完成
     */
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return exec.invokeAny(tasks);
    }

    /**
     * 执行给定的任务
     * 如果在给定的超时期满前某个任务已成功完成（也就是未抛出异常），则返回其结果。 一旦正常或异常返回后，则取消尚未完成的任务。
     * 如果此操作正在进行时修改了给定的collection，则此方法的结果是不确定的。
     *
     * @param tasks   任务集合
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @param <T>     泛型
     * @return 某个任务返回的结果
     * @throws InterruptedException 如果等待时发生中断
     * @throws ExecutionException   如果没有任务成功完成
     * @throws TimeoutException     如果在所有任务成功完成之前给定的超时期满
     */
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return exec.invokeAny(tasks, timeout, unit);
    }

    /**
     * 延迟执行Runnable命令
     *
     * @param command 命令
     * @param delay   延迟时间
     * @param unit    单位
     * @return 表示挂起任务完成的ScheduledFuture，并且其{@code get()}方法在完成后将返回{@code null}
     */
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        return scheduleExec.schedule(command, delay, unit);
    }

    /**
     * 延迟执行Callable命令
     *
     * @param callable 命令
     * @param delay    延迟时间
     * @param unit     时间单位
     * @param <V>      泛型
     * @return 可用于提取结果或取消的ScheduledFuture
     */
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
        return scheduleExec.schedule(callable, delay, unit);
    }

    /**
     * 延迟并循环执行命令
     *
     * @param command      命令
     * @param initialDelay 首次执行的延迟时间
     * @param period       连续执行之间的周期
     * @param unit         时间单位
     * @return 表示挂起任务完成的ScheduledFuture，并且其{@code get()}方法在取消后将抛出异常
     */
    public ScheduledFuture<?> scheduleWithFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
        return scheduleExec.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * 延迟并以固定休息时间循环执行命令
     *
     * @param command      命令
     * @param initialDelay 首次执行的延迟时间
     * @param delay        每一次执行终止和下一次执行开始之间的延迟
     * @param unit         时间单位
     * @return 表示挂起任务完成的ScheduledFuture，并且其{@code get()}方法在取消后将抛出异常
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
        return scheduleExec.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }


}
