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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 线程池封装工具类。
 * <p>
 * 整合 {@link ExecutorService} 和 {@link ScheduledExecutorService}，
 * 提供固定线程、缓存线程、单线程三种模式，以及定时/周期任务支持。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public class ThreadPool {

    /** 固定线程数目的线程池 */
    public static final int FixedThread = 0;

    /** 缓冲功能的线程池 */
    public static final int CachedThread = 1;

    /** 单线程的线程池 */
    public static final int SingleThread = 2;

    /** 常规任务执行线程池 */
    private final ExecutorService exec;

    /** 定时/周期任务执行线程池 */
    private final ScheduledExecutorService scheduleExec;

    /** 线程池核心大小 */
    private final int corePoolSize;

    /**
     * 构造线程池
     *
     * @param type         线程池类型（{@link #FIXED_THREAD}, {@link #CACHED_THREAD}, {@link #SINGLE_THREAD}）
     * @param corePoolSize 核心线程数（仅对 FIXED 和 SCHEDULED 类型生效）
     * @throws IllegalArgumentException 如果 type 无效
     */
    public ThreadPool(final int type, final int corePoolSize) {
        this.corePoolSize = corePoolSize;
        this.scheduleExec = Executors.newScheduledThreadPool(corePoolSize);

        switch (type) {
            case FixedThread:
                exec = Executors.newFixedThreadPool(corePoolSize);
                break;
            case SingleThread:
                exec = Executors.newSingleThreadExecutor();
                break;
            case CachedThread:
                exec = Executors.newCachedThreadPool();
                break;
            default:
                throw new IllegalArgumentException("Unknown thread pool type: " + type);
        }
    }

    /**
     * 获取线程池核心大小
     *
     * @return 核心线程数
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 提交单个任务执行
     *
     * @param command 任务
     */
    public void execute(final Runnable command) {
        exec.execute(command);
    }

    /**
     * 批量提交任务执行
     *
     * @param commands 任务列表
     */
    public void execute(final List<Runnable> commands) {
        for (Runnable command : commands) {
            exec.execute(command);
        }
    }

    /**
     * 优雅关闭线程池。已提交的任务将继续执行，但不接受新任务。
     */
    public void shutdown() {
        exec.shutdown();
        scheduleExec.shutdown();
    }

    /**
     * 立即关闭线程池，尝试取消所有正在执行的任务。
     *
     * @return 等待执行的任务列表
     */
    public List<Runnable> shutdownNow() {
        scheduleExec.shutdownNow();
        return exec.shutdownNow();
    }

    /**
     * 判断线程池是否已关闭
     *
     * @return {@code true} 如果已调用 {@link #shutdown()}
     */
    public boolean isShutDown() {
        return exec.isShutdown();
    }

    /**
     * 判断所有任务是否已完成（需先调用 shutdown）
     *
     * @return {@code true} 如果所有任务已完成
     */
    public boolean isTerminated() {
        return exec.isTerminated();
    }

    /**
     * 阻塞等待所有任务完成或超时
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return {@code true} 如果所有任务在超时前完成
     * @throws InterruptedException 等待时被中断
     */
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }

    // ===================== 任务提交 =====================

    /**
     * 提交 Callable 任务
     *
     * @param task 任务
     * @param <T>  返回类型
     * @return Future
     */
    public <T> Future<T> submit(final Callable<T> task) {
        return exec.submit(task);
    }

    /**
     * 提交 Runnable 任务并指定返回值
     *
     * @param task   任务
     * @param result 完成后的返回值
     * @param <T>    返回类型
     * @return Future
     */
    public <T> Future<T> submit(final Runnable task, final T result) {
        return exec.submit(task, result);
    }

    /**
     * 提交 Runnable 任务
     *
     * @param task 任务
     * @return Future（get 返回 null）
     */
    public Future<?> submit(final Runnable task) {
        return exec.submit(task);
    }

    // ===================== 批量任务 =====================

    /**
     * 执行所有任务，全部完成后返回结果列表
     *
     * @param tasks 任务集合
     * @param <T>   返回类型
     * @return Future 列表
     * @throws InterruptedException 等待时被中断
     */
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return exec.invokeAll(tasks);
    }

    /**
     * 执行所有任务，超时后返回结果列表
     *
     * @param tasks   任务集合
     * @param timeout 超时时间
     * @param unit    时间单位
     * @param <T>     返回类型
     * @return Future 列表
     * @throws InterruptedException 等待时被中断
     */
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
                                         final long timeout, final TimeUnit unit) throws InterruptedException {
        return exec.invokeAll(tasks, timeout, unit);
    }

    /**
     * 执行任务，返回第一个成功完成的结果
     *
     * @param tasks 任务集合
     * @param <T>   返回类型
     * @return 某个任务的结果
     * @throws InterruptedException 等待时被中断
     * @throws ExecutionException   没有任务成功完成
     */
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return exec.invokeAny(tasks);
    }

    /**
     * 执行任务，超时前返回第一个成功完成的结果
     *
     * @param tasks   任务集合
     * @param timeout 超时时间
     * @param unit    时间单位
     * @param <T>     返回类型
     * @return 某个任务的结果
     * @throws InterruptedException 等待时被中断
     * @throws ExecutionException   没有任务成功完成
     * @throws TimeoutException     超时
     */
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
                           final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return exec.invokeAny(tasks, timeout, unit);
    }

    // ===================== 定时任务 =====================

    /**
     * 延迟执行 Runnable 任务
     *
     * @param command 任务
     * @param delay   延迟时间
     * @param unit    时间单位
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        return scheduleExec.schedule(command, delay, unit);
    }

    /**
     * 延迟执行 Callable 任务
     *
     * @param callable 任务
     * @param delay    延迟时间
     * @param unit     时间单位
     * @param <V>      返回类型
     * @return ScheduledFuture
     */
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
        return scheduleExec.schedule(callable, delay, unit);
    }

    /**
     * 以固定频率重复执行任务
     *
     * @param command      任务
     * @param initialDelay 首次延迟
     * @param period       执行间隔
     * @param unit         时间单位
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> scheduleWithFixedRate(final Runnable command,
                                                    final long initialDelay, final long period, final TimeUnit unit) {
        return scheduleExec.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * 以固定延迟重复执行任务（上次执行结束后等待指定时间再执行下次）
     *
     * @param command      任务
     * @param initialDelay 首次延迟
     * @param delay        两次执行之间的延迟
     * @param unit         时间单位
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
                                                     final long initialDelay, final long delay, final TimeUnit unit) {
        return scheduleExec.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
}
