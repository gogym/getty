
package com.gettyio.core.util.thread;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AutoLock类实现了AutoCloseable和Serializable接口。
 * 该类主要用于提供一种自动锁机制，确保资源的正确释放，
 * 同时由于实现了Serializable接口，该锁对象可以被序列化。
 */
public class AutoLock implements AutoCloseable, Serializable
{
    private static final long serialVersionUID = 3300696774541816341L;

    // 创建一个私有的、可重入的锁实例，用于同步控制。
    private final ReentrantLock _lock = new ReentrantLock();


    /**
     * 获取锁。
     * <p>此方法将尝试获取锁。如果锁已被其他线程持有，当前线程将被阻塞，直到获取到锁为止。</p>
     *
     * @return 返回当前AutoLock实例，用于后续的解锁操作。
     */
    public AutoLock lock()
    {
        _lock.lock(); // 尝试获取锁，如果锁不可用则阻塞线程
        return this; // 返回当前AutoLock实例，允许链式调用
    }


    /**
     * 判断当前线程是否持有该锁。
     * 本方法不接受任何参数。
     *
     * @return 如果当前线程持有该锁，则返回true；否则返回false。
     * @see ReentrantLock#isHeldByCurrentThread() 对应ReentrantLock类中的isHeldByCurrentThread方法，用于检查当前线程是否持有锁。
     */
    public boolean isHeldByCurrentThread()
    {
        return _lock.isHeldByCurrentThread(); // 查询当前线程是否持有锁
    }


    /**
     * 创建并返回与当前锁相关联的条件变量。
     * 这个方法允许在锁的上下文中进行复杂的同步操作，比如等待特定条件的满足或通知一组等待线程。
     *
     * @return 与当前锁相关联的{@link Condition}实例，用于实现高级别的同步控制。
     */
    public Condition newCondition()
    {
        return _lock.newCondition(); // 创建并返回与当前锁相关联的条件变量
    }


    /**
     * 仅用于测试的包私有方法。
     * 检查当前对象是否被锁定。
     *
     * @return 布尔值，如果对象当前被锁定，则返回true；否则返回false。
     */
    boolean isLocked()
    {
        return _lock.isLocked(); // 检查底层锁是否被锁定
    }


    /**
     * 关闭并释放锁。
     * 此方法是资源清理操作，用于在不再需要锁时释放锁资源。
     * 没有参数。
     * 没有返回值。
     */
    @Override
    public void close()
    {
        _lock.unlock(); // 释放锁
    }


    /**
     * <p>A reentrant lock with a condition that can be used in a try-with-resources statement.</p>
     * <p>Typical usage:</p>
     * <pre>
     * // Waiting
     * try (AutoLock lock = _lock.lock())
     * {
     *     lock.await();
     * }
     *
     * // Signaling
     * try (AutoLock lock = _lock.lock())
     * {
     *     lock.signalAll();
     * }
     * </pre>
     */
    public static class WithCondition extends AutoLock
    {
        // 创建一个私有的_condition实例，它是NewCondition类的一个实例。
        private final Condition _condition = newCondition();

        /**
         * 获取一个带条件的自动锁。
         * 该方法重写了父类的lock方法，返回一个WithCondition类型的对象，允许用户在锁定状态下基于条件进行等待和通知。
         *
         * @return 返回一个AutoLock.WithCondition对象，提供条件等待和通知的功能。
         */
        @Override
        public WithCondition lock()
        {
            return (WithCondition)super.lock();
        }

        /**
         * 唤醒一个等待在该条件变量上的线程。
         * 该方法对应于Condition接口的signal方法，当有线程正在该条件变量上等待时，会随机唤醒一个等待线程。
         * 注意：调用此方法前应确保已获取到锁（通过lock()方法）。
         */
        public void signal()
        {
            _condition.signal();
        }

        /**
         * 唤醒所有等待在该条件变量上的线程。
         * 该方法对应于Condition接口的signalAll方法，会唤醒所有当前等待在该条件变量上的线程。
         * 注意：调用此方法前应确保已获取到锁（通过lock()方法）。
         */
        public void signalAll()
        {
            _condition.signalAll();
        }


        /**
         * 使当前线程在满足特定条件前等待。此方法会释放当前线程持有的锁，并将当前线程放入等待状态，直到其他线程通过signal()或signalAll()方法唤醒它。
         *
         * @throws InterruptedException 如果当前线程在等待过程中被中断，则抛出InterruptedException。
         * 注意：调用此方法前应确保已获取到锁（通过lock()方法）。
         * @see Condition#await()
         */
        public void await() throws InterruptedException
        {
            _condition.await();
        }


        /**
         * 使当前线程在满足特定条件前等待一段时间。如果在指定的时间内条件未满足，则方法返回。此方法会释放当前线程持有的锁，并将当前线程放入等待状态。
         *
         * @param time 等待的时长
         * @param unit 等待时间的单位
         * @return 如果等待时间未到期则返回true，否则返回false。
         * @throws InterruptedException 如果当前线程在等待过程中被中断，则抛出InterruptedException。
         * 注意：调用此方法前应确保已获取到锁（通过lock()方法）。
         * @see Condition#await(long, TimeUnit)
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException
        {
            return _condition.await(time, unit);
        }

    }
}