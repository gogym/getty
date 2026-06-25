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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自动锁工具类，实现 {@link AutoCloseable} 以支持 try-with-resources 语法。
 * <p>
 * 典型用法：
 * <pre>
 * AutoLock lock = new AutoLock();
 * try (AutoLock held = lock.lock()) {
 *     // 临界区代码
 * }
 * // 锁自动释放
 * </pre>
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public class AutoLock implements AutoCloseable, Serializable {

    private static final long serialVersionUID = 3300696774541816341L;

    /** 底层可重入锁 */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 获取锁。如果锁已被其他线程持有，当前线程将阻塞。
     *
     * @return 当前 AutoLock 实例（用于 try-with-resources）
     */
    public AutoLock lock() {
        lock.lock();
        return this;
    }

    /**
     * 判断当前线程是否持有该锁
     *
     * @return {@code true} 如果当前线程持有锁
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * 创建与此锁关联的条件变量
     *
     * @return 关联的 {@link Condition} 实例
     */
    public Condition newCondition() {
        return lock.newCondition();
    }

    /**
     * 检查锁是否被任意线程持有（包私有，仅供测试使用）
     *
     * @return {@code true} 如果锁被持有
     */
    boolean isLocked() {
        return lock.isLocked();
    }

    /**
     * 释放锁（由 try-with-resources 自动调用）
     */
    @Override
    public void close() {
        lock.unlock();
    }

    /**
     * 带有条件变量的可重入自动锁。
     * <p>
     * 典型用法：
     * <pre>
     * AutoLock.WithCondition lock = new AutoLock.WithCondition();
     *
     * // 等待
     * try (AutoLock.WithCondition held = lock.lock()) {
     *     held.await();
     * }
     *
     * // 通知
     * try (AutoLock.WithCondition held = lock.lock()) {
     *     held.signalAll();
     * }
     * </pre>
     * </p>
     */
    public static class WithCondition extends AutoLock {

        private static final long serialVersionUID = 1L;

        /** 与此锁关联的条件变量 */
        private final Condition condition = newCondition();

        @Override
        public WithCondition lock() {
            return (WithCondition) super.lock();
        }

        /**
         * 唤醒一个在此条件变量上等待的线程
         */
        public void signal() {
            condition.signal();
        }

        /**
         * 唤醒所有在此条件变量上等待的线程
         */
        public void signalAll() {
            condition.signalAll();
        }

        /**
         * 使当前线程在此条件变量上等待，直到被唤醒
         *
         * @throws InterruptedException 等待时被中断
         */
        public void await() throws InterruptedException {
            condition.await();
        }

        /**
         * 使当前线程在此条件变量上等待指定时间
         *
         * @param time 等待时长
         * @param unit 时间单位
         * @return {@code false} 如果等待超时
         * @throws InterruptedException 等待时被中断
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return condition.await(time, unit);
        }
    }
}
