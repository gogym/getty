package com.gettyio.core.util.timer;

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonBlockQueue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

import static com.gettyio.core.util.StringUtil.simpleClassName;


/**
 * 时间轮
 * 参考自netty 4.3
 */
public class HashedWheelTimer implements Timer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HashedWheelTimer.class);


    /*****************************************************************/

    //--动态配置提前执行
    private volatile boolean dynamicOpen;
    //存放纳秒--worker 初始化 startTime
    private volatile long startTime;
    //存放纳秒--tick的时长，即指针多久转一格
    private final long tickDuration;
    //轮子基本结构
    private final HashedWheelBucket[] wheel;
    //用来快速计算任务应该放的格子--位运算
    private final int mask;
    //用来阻塞 start()的线程
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);
    //待执行任务队列
    private final LinkedNonBlockQueue<HashedWheelTimeout> timeouts = new LinkedNonBlockQueue<>();
    //待取消任务队列
    private final LinkedNonBlockQueue<HashedWheelTimeout> cancelledTimeouts = new LinkedNonBlockQueue<>();
    //等待处理计数器
    private final AtomicLong pendingTimeouts = new AtomicLong(0);
    //最大等待处理次数
    private final long maxPendingTimeouts;

    /*****************************************************************/
    //AtomicIntegerFieldUpdater是JUC里面的类，原理是利用反射进行原子操作。有比AtomicInteger更好的性能和更低得内存占用
    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");
    //声明 worker
    private final Worker worker = new Worker();
    //worker 线程
    private final Thread workerThread;
    //定义worker的3个状态：初始化、启动、关闭
    public static final int WORKER_STATE_INIT = 0;
    public static final int WORKER_STATE_STARTED = 1;
    public static final int WORKER_STATE_SHUTDOWN = 2;
    //当前时间轮的状态
    public volatile int workerState = WORKER_STATE_INIT;


    /*****************************************************************/
    //实例计数器
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    //警告太多的实例
    private static final AtomicBoolean WARNED_TOO_MANY_INSTANCES = new AtomicBoolean();
    //实例数限制
    private static final int INSTANCE_COUNT_LIMIT = 64;

    /*****************************************************************/
    //内存泄露检测器
    //private static final ResourceLeakDetector<HashedWheelTimer> leakDetector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(HashedWheelTimer.class, 1);
    // private final ResourceLeakTracker<HashedWheelTimer> leak;

    /*****************************************************************/


    /**
     * 时间轮的构造函数
     *
     * @param threadFactory      {@link ThreadFactory}用来创建执行{@link TimerTask}的 worker 线程 {@link Thread}
     * @param tickDuration       tick的时长，即指针多久转一格
     * @param unit               {@code tickDuration}的时间单位
     * @param ticksPerWheel      每圈几格
     * @param leakDetection      是否开启内存泄露检测；
     *                           默认设置{@code true}
     *                           如果工作线程不是守护线程，需设置 false
     * @param maxPendingTimeouts 最大等待处理次数；
     *                           调用{@code newTimeout}的次数超过最大等待处理次数，将抛出异常{@link java.util.concurrent.RejectedExecutionException}
     *                           如果该值为0或负值，则不考虑最大超时时间限制。
     */
    public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration, TimeUnit unit, int ticksPerWheel, boolean leakDetection, long maxPendingTimeouts) {


        //参数校验
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be greater than 0: " + tickDuration);
        }
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }

        //创建时间轮基本的数据结构，一个数组，长度为不小于 ticksPerWheel 的最小2的 n 次方
        wheel = createWheel(ticksPerWheel);
        //这是一个表示符，用来快速计算任务应该放的格子
        // 了解，给定一个deadline的定时任务，其应该呆的格子=deadline%wheel.length.但是%操作是个相对耗时的操作，
        // 所以使用一种变通的位运算代替：
        // 因为一圈的长度为2的n次方，mask = 2^n-1后低位将全部是1，然后deadline&mast == deadline%wheel.length
        // java中的HashMap也是使用这种处理方法
        mask = wheel.length - 1;

        //转换成纳秒处理
        this.tickDuration = unit.toNanos(tickDuration);

        //校验是否内存溢出。即指针转动的时间间隔不能太长而导致 tickDuration*wheel.length>Long.MAX_VALUE
        if (this.tickDuration >= Long.MAX_VALUE / wheel.length) {
            throw new IllegalArgumentException(String.format("tickDuration: %d (expected: 0 < tickDuration in nanos < %d)", tickDuration, Long.MAX_VALUE / wheel.length));
        }
        //创建 worker 线程
        workerThread = threadFactory.newThread(worker);

        //这里默认是启动内存泄露检测：当HashedWheelTimer实例超过当前cpu可用核数 *4的时候，将发出警告
        //leak = leakDetection || !workerThread.isDaemon() ? leakDetector.track(this) : null;

        //设置最大等待处理次数
        this.maxPendingTimeouts = maxPendingTimeouts;

        //log 警告太多的实例
        if (INSTANCE_COUNTER.incrementAndGet() > INSTANCE_COUNT_LIMIT && WARNED_TOO_MANY_INSTANCES.compareAndSet(false, true)) {
            reportTooManyInstances();
        }
    }

    /**
     * 创建时间轮 - 使用默认线程工厂，100ms 转一格， 512格 ，启用内存溢出检测，不设置最大等待处理次数
     */
    public HashedWheelTimer() {
        this(Executors.defaultThreadFactory(), 100, TimeUnit.MILLISECONDS, 512);
    }

    /**
     * 创建时间轮 - 使用默认线程工厂，512格 ，启用内存溢出检测，不设置最大等待处理次数
     *
     * @param tickDuration tick的时长，即指针多久转一格
     * @param unit         {@code tickDuration}的时间单位
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit) {
        this(Executors.defaultThreadFactory(), tickDuration, unit, 512);
    }


    /**
     * 创建时间轮 - 使用默认线程工厂，启用内存溢出检测，不设置最大等待处理次数
     *
     * @param tickDuration  tick的时长，即指针多久转一格
     * @param unit          {@code tickDuration}的时间单位
     * @param ticksPerWheel 每圈几格
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel);
    }


    /**
     * 创建时间轮 - 启用内存溢出检测，不设置最大等待处理次数
     *
     * @param threadFactory {@link ThreadFactory}用来创建执行{@link TimerTask}的 worker 线程 {@link Thread}
     * @param tickDuration  tick的时长，即指针多久转一格
     * @param unit          {@code tickDuration}的时间单位
     * @param ticksPerWheel 每圈几格
     *                      默认设置{@code true}
     *                      如果工作线程不是守护线程，需设置 false
     */
    public HashedWheelTimer(
            ThreadFactory threadFactory,
            long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, true);
    }

    /**
     * 创建时间轮 -不设置 最大等待处理次数
     *
     * @param threadFactory {@link ThreadFactory}用来创建执行{@link TimerTask}的 worker 线程 {@link Thread}
     * @param tickDuration  tick的时长，即指针多久转一格
     * @param unit          {@code tickDuration}的时间单位
     * @param ticksPerWheel 每圈几格
     * @param leakDetection 是否开启内存泄露检测；
     *                      默认设置{@code true}
     *                      如果工作线程不是守护线程，需设置 false
     */
    public HashedWheelTimer(
            ThreadFactory threadFactory,
            long tickDuration, TimeUnit unit, int ticksPerWheel, boolean leakDetection) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, leakDetection, -1);
    }


    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        // 参数校验
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        //等待处理计数器加一
        long pendingTimeoutsCount = pendingTimeouts.incrementAndGet();

        //设置最大等待处理次数 并 等待处理计数器 大于 最大等待处理次数
        if (maxPendingTimeouts > 0 && pendingTimeoutsCount > maxPendingTimeouts) {
            pendingTimeouts.decrementAndGet();
            throw new RejectedExecutionException("Number of pending timeouts ("
                    + pendingTimeoutsCount + ") is greater than or equal to maximum allowed pending "
                    + "timeouts (" + maxPendingTimeouts + ")");
        }
        // 如果时间轮没有启动，则启动
        start();

        //计算任务的deadline
        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime;

        //这里定时任务不是直接加到对应的格子中，
        //而是先加入到一个待执行任务队列里，然后等到下一个tick的时候，会从队列里取出最多10w个任务加入到指定的格子中

        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadline);
        try {
            timeouts.put(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return timeout;
    }

    // 启动时间轮。
    // 这个方法其实不需要显示的主动调用，因为在添加定时任务（newTimeout()方法）的时候会自动调用此方法。
    // 这个是合理的设计，因为如果时间轮里根本没有定时任务，启动时间轮也是空耗资源
    public void start() {
        // 判断当前时间轮的状态
        // 如果是初始化，则启动worker线程，启动整个时间轮；
        // 如果已经启动，则略过；
        // 如果已经停止，则报错
        // 这里是一个Lock Free的设计。因为可能有多个线程调用启动方法，这里使用AtomicIntegerFieldUpdater原子的更新时间轮的状态
        switch (WORKER_STATE_UPDATER.get(this)) {
            case WORKER_STATE_INIT:
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                throw new IllegalStateException("cannot be started once stopped");
            default:
                throw new Error("Invalid WorkerState");
        }

        // 等待worker线程初始化时间轮的启动时间
        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {
                // Ignore - it will be ready very soon.
            }
        }
    }


    //停止时间轮的方法
    @Override
    public Set<Timeout> stop() {
        // worker线程不能停止时间轮，也就是加入的定时任务，不能调用这个方法。
        // 不然会有恶意的定时任务调用这个方法而造成大量定时任务失效
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException(HashedWheelTimer.class.getSimpleName() + ".stop() cannot be called from " + TimerTask.class.getSimpleName());
        }

        // 尝试CAS替换当前状态为--"停止：2"。
        // 如果失败，则当前时间轮的状态只能是--"初始化：0"或者"停止：2"。直接将当前状态设置为--"停止：2"
        if (!WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                //实例计数器减一
                INSTANCE_COUNTER.decrementAndGet();
//                if (leak != null) {
//                    boolean closed = leak.close(this);
//                    assert closed;
//                }
            }
            return Collections.emptySet();
        }

        // 终端 worker 线程
        //  interrupt()只是改变中断状态而已:
        // interrupt()不会中断一个正在运行的线程。
        // 这一方法实际上完成的是，在线程受到阻塞时抛出一个中断信号，这样线程就得以退出阻塞的状态。
        // 更确切的说，如果线程被Object.wait, Thread.join和Thread.sleep三种方法之一阻塞，
        // 那么，它将接收到一个中断异常（InterruptedException），从而提早地终结被阻塞状态.
        // 如果线程没有被阻塞，这时调用interrupt()将不起作用；仅仅是设置中断标志位为true
        try {
            boolean interrupted = false;

            while (workerThread.isAlive()) {
                //改变中断状态
                workerThread.interrupt();
                try {
                    //触发中断异常
                    workerThread.join(100);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }

            //当前线程中断
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } finally {
            //实例计数器加一
            INSTANCE_COUNTER.decrementAndGet();
//            if (leak != null) {
//                boolean closed = leak.close(this);
//                assert closed;
//            }
        }

        // 返回未处理的任务
        return worker.unprocessedTimeouts();
    }

    @Override
    public void openSwitch() {
        dynamicOpen = true;
        logger.info("HashedWheelTimer openSwitch");
    }

    @Override
    public void closeSwitch() {
        dynamicOpen = false;
        logger.info("HashedWheelTimer closeSwitch");
    }


    //初始化时间轮
    private static HashedWheelBucket[] createWheel(int ticksPerWheel) {
        //参数校验
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException("ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }

        // 初始化 ticksPerWheel 的值为 不小于 ticksPerWheel 的最小2的n次方
        ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);

        //初始化 wheel 数组
        HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new HashedWheelBucket();
        }

        return wheel;
    }


    // 初始化 ticksPerWheel 的值为 不小于 ticksPerWheel 的最小2的n次方
    //这里其实不建议使用这种方式，因为当ticksPerWheel的值很大的时候，这个方法会循环很多次，方法执行时间不稳定，效率也不够。
    private static int normalizeTicksPerWheelOld(int ticksPerWheel) {
        int normalizedTicksPerWheel = 1;
        while (normalizedTicksPerWheel < ticksPerWheel) {
            normalizedTicksPerWheel <<= 1;
        }

        return normalizedTicksPerWheel;
    }

    //推荐使用java8 HashMap的做法：
    private static int normalizeTicksPerWheel(int ticksPerWheel) {
        //这里参考java8 hashmap的算法，使推算的过程固定
        int n = ticksPerWheel - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        // 这里1073741824 = 2^30,防止溢出
        return (n < 0) ? 1 : (n >= 1073741824) ? 1073741824 : n + 1;
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            // 该对象进行 GC 完成时，进行判断
            // 如果我们还没有关闭，然后我们要确保减少活动实例计数。
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet();
            }
        }
    }

    //警告太多的实例
    private static void reportTooManyInstances() {
        String resourceType = simpleClassName(HashedWheelTimer.class);
        logger.error("You are creating too many " + resourceType + " instances. " + resourceType + " is a shared resource that must be reused across the JVM," + "so that only a few instances are created.");
    }


    /*=====================================================*/
    /*                      Worker                         */
    /*=====================================================*/


    //worker 是时间轮的核心线程类。 tick 的转动，过期任务的处理都是在这个线程中处理的
    private final class Worker implements Runnable {
        //未处理任务列表
        private final Set<Timeout> unprocessedTimeouts = new HashSet<Timeout>();

        private long tick;

        @Override
        public void run() {
            //初始化 startTime. 只有所有任务的 deadline 都是相对于这个时间点
            startTime = System.nanoTime();
            //由于System.nanoTime()可能返回0，甚至负数。
            // 并且0是一个标识符，用来判断startTime是否被初始化，所以当startTime=0的时候，重新赋值为1
            if (startTime == 0) {
                startTime = 1;
            }
            //唤醒阻塞在start()的线程
            startTimeInitialized.countDown();

            //只要时间轮的状态为 WORKER_STATE_STARTED就循环的 "转动" tick，循环判断响应格子中的到期任务
            do {
                final long deadline = waitForNextTick();
                // 可能溢出或者被中断的时候会返回负数, 所以小于等于0不管
                if (deadline > 0) {
                    //获取 tick 对应的格子索引
                    int idx = (int) (tick & mask);
                    //移除被取消的任务
                    processCancelledTasks();
                    HashedWheelBucket bucket = wheel[idx];
                    //从任务队列中取出任务加入到对应的格子中
                    transferTimeoutsToBuckets();
                    //过期执行格子中的任务---或是开启开关的
                    bucket.expireTimeouts(deadline, dynamicOpen);
                    tick++;
                }
            } while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED);

            // 这里应该是时间轮停止了，清除所有格子中的任务，并加入到未处理任务列表，以供stop()方法返回
            for (HashedWheelBucket bucket : wheel) {
                bucket.clearTimeouts(unprocessedTimeouts);
            }
            // 将还没有加入到格子中的待处理定时任务队列中的任务取出，
            // 如果是未取消的任务，则加入到未处理任务队列中，以供stop()方法返回
            for (; ; ) {
                HashedWheelTimeout timeout = null;
                try {
                    timeout = timeouts.poll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (timeout == null) {
                    break;
                }
                if (!timeout.isCancelled()) {
                    unprocessedTimeouts.add(timeout);
                }
            }

            //处理取消的任务
            processCancelledTasks();
        }

        //返回不可修改的 未处理任务列表 视图
        public Set<Timeout> unprocessedTimeouts() {
            return Collections.unmodifiableSet(unprocessedTimeouts);
        }


        //将 newTimeout() 方法中 待处理定时任务队列中的任务加入到指定的格子中
        private void transferTimeoutsToBuckets() {
            //每次 tick 只处理 10w个任务，以免阻塞 worker 线程
            for (int i = 0; i < 100000; i++) {
                HashedWheelTimeout timeout = null;
                try {
                    timeout = timeouts.poll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //如果没有任务了，直接跳出循环
                if (timeout == null) {
                    break;
                }
                //还没有放入到格子中就取消了，直接略过
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    continue;
                }

                //如果动态开关打开，直接调用执行任务，不进入格子
                if (dynamicOpen) {
//                    logger.info("-----未进格子node-----:" + timeout);
                    timeout.expire();
                    continue;
                }

                //计算任务需要经过多少个 tick
                long calculated = timeout.deadline / tickDuration;
                //计算任务的轮数
                timeout.remainingRounds = (calculated - tick) / wheel.length;

                //如果任务在 timeouts 队列里面放久了，以至于已经过了执行时间，
                //这个时候就使用当前tick, 也就是放到当前 bucket, 此方法调用完后就会被执行.
                final long ticks = Math.max(calculated, tick);
                int stopIndex = (int) (ticks & mask);

                //将任务加入到相应的格子中
                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
//                logger.info("=====进入格子node=====:" + timeout);
            }
        }

        //将取消的任务取出，并从格子中移除
        private void processCancelledTasks() {
            for (; ; ) {
                HashedWheelTimeout timeout = null;
                try {
                    timeout = cancelledTimeouts.poll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (timeout == null) {
                    break;
                }
                try {
                    //从格子中移除自身
                    timeout.remove();
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("An exception was thrown while process a cancellation task", t);
                    }
                }
            }
        }

        //sleep,直到下次 tick 到来，然后返回该次 tick 和启动时间之间的时长
        private long waitForNextTick() {
            //下次 tick 的时间点，用于计算需要 sleep 的时间
            long deadline = tickDuration * (tick + 1);
//            System.out.println("tick=" + tick + ", deadline=" + deadline);

            for (; ; ) {
                //计算需要 sleep 的时间，之所以加 999999 后再除以 1000000，是为了保证足够的 sleep 时间
                //例如：当 deadline - currentTime = 2000002 的时候，如果不加 999999，则只睡了 2ms
                //而 2ms 其实是未达到 deadline 这个时间点的，所以为了使上述情况能 sleep 足够的时间，加上999999后，会多睡1ms
                final long currentTime = System.nanoTime() - startTime;
                long sleepTimeMs = (deadline - currentTime + 999999) / 1000000;//毫秒
//                System.out.println("deadline=" + deadline + ",currentTime=" + currentTime + ",sleepTimeMs=" + sleepTimeMs);
                if (sleepTimeMs <= 0) {
                    if (currentTime == Long.MIN_VALUE) {
                        return -Long.MAX_VALUE;
                    } else {
                        return currentTime;
                    }
                }
                // 这里是因为windows平台的定时调度最小单位为10ms，如果不是10ms的倍数，可能会引起sleep时间不准确
//                if (PlatformDependent.isWindows()) {
//                    sleepTimeMs = sleepTimeMs / 10 * 10;
//                }

                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    //调用HashedWheelTimer.stop()时优雅退出
                    if (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE;
                    }
                }

            }

        }
    }// worker end




    /*=====================================================*/
    /*             HashedWheelBucket                       */
    /*=====================================================*/

    /**
     * HashedWheelBucket 用来存放 HashedWheelTimeout，结构类似于 LinkedList。
     * 提供了 expireTimeouts(long deadline) 方法来过期并执行格子中的定时任务
     */
    private static final class HashedWheelBucket {
        //LinkedList结构
        //指向格子中任务的首尾
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        //基础链表添加操作
        public void addTimeout(HashedWheelTimeout timeout) {
            assert timeout.bucket == null;
            timeout.bucket = this;
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
        }

        //过期并执行格子中的到期任务，tick 到该格子的时候，worker 线程会调用这个方法，根据 deadline 和 remainingRounds 判断任务是否过期
        public void expireTimeouts(long deadline, boolean dynamicOpen) {
            HashedWheelTimeout timeout = head;

            //遍历格子中的所有定时任务
            while (timeout != null) {
                //先保存 next ，因为移除后 next 将被设置为 null
                HashedWheelTimeout next = timeout.next;
                //开启动态开关--直接执行任务
                if (dynamicOpen) {
                    next = remove(timeout);
                    timeout.expire();
                } else if (timeout.remainingRounds <= 0) { //定时任务到期
                    next = remove(timeout);

                    //过期并执行任务
                    if (timeout.deadline <= deadline) {
                        timeout.expire();
                    } else {
                        //如果 round 数已经为0,deadline 却大于当前格子的 deadline，说放错格子了，这种情况应该不会出现
                        throw new IllegalStateException(String.format("timeout.deadline (%d) > deadline (%d)", timeout.deadline, deadline));
                    }
                } else if (timeout.isCancelled()) {//定时任务被取消
                    next = remove(timeout);
                } else {
                    timeout.remainingRounds--;
                }

                timeout = next;
            }
        }

        //基础链表 移除 node 操作
        public HashedWheelTimeout remove(HashedWheelTimeout timeout) {
            HashedWheelTimeout next = timeout.next;
            //删除已处理或取消的任务，更新链表
            if (timeout.prev != null) {
                timeout.prev.next = next;
            }
            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            }

            if (timeout == head) {
                //即使头也是尾
                if (timeout == tail) {
                    tail = null;
                    head = null;
                } else {
                    head = next;
                }
            } else if (timeout == tail) {
                //如果是尾部，尾部向前更新一个节点
                tail = timeout.prev;
            }
            //prev, next and bucket 附空并允许 GC
            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
            //计数器减一
            timeout.timer.pendingTimeouts.decrementAndGet();

            return next;
        }

        //清除格子并返回所有未过期、未取消的任务
        public void clearTimeouts(Set<Timeout> set) {
            for (; ; ) {
                HashedWheelTimeout timeout = pollTimeout();
                if (timeout == null) {
                    return;
                }
                if (timeout.isExpired() || timeout.isCancelled()) {
                    continue;
                }
                //未过期、未取消
                set.add(timeout);
            }
        }

        //链表的 poll 操作
        private HashedWheelTimeout pollTimeout() {
            HashedWheelTimeout head = this.head;
            if (head == null) {
                return null;
            }
            HashedWheelTimeout next = head.next;
            if (next == null) {
                tail = this.head = null;
            } else {
                this.head = next;
                next.prev = null;
            }
            //prev, next and bucket 附空并允许 GC
            head.prev = null;
            head.next = null;
            head.bucket = null;
            return head;

        }


    } //HashedWheelBucket end


    /*=====================================================*/
    /*             HashedWheelTimeout                      */
    /*=====================================================*/

    /**
     * HashedWheelTimeout是一个定时任务的内部包装类，双向链表结构。会保存 定时任务到期执行的任务、deadline、round等信息
     */
    private static final class HashedWheelTimeout implements Timeout {

        //定义定时任务的3个状态：初始化、取消、过期
        private static final int ST_INIT = 0;
        private static final int ST_CANCELLED = 1;
        private static final int ST_EXPIRED = 2;

        //用 CAS (比较并交换-乐观锁) 方式更新定时任务状态
        private static final AtomicIntegerFieldUpdater<HashedWheelTimeout> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimeout.class, "state");


        //时间轮引用
        private final HashedWheelTimer timer;
        //具体到期需要执行的任务
        private final TimerTask task;
        //最后期限
        private final long deadline;


        private volatile int state = ST_INIT;
        //离任务执行的轮数
        //当将此任务加入到格子中时，计算该值 。 每过一轮，该值减一
        long remainingRounds;

        //使用双向链表结构，由于只有 worker 线程访问，这里不需要 synchronization/volatile
        HashedWheelTimeout next;
        HashedWheelTimeout prev;


        //定时任务所在的格子
        HashedWheelBucket bucket;

        HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadline) {
            this.timer = timer;
            this.task = task;
            this.deadline = deadline;
        }


        @Override
        public Timer timer() {
            return timer;
        }

        @Override
        public TimerTask task() {
            return task;
        }

        @Override
        public boolean cancel() {
            //这里只是修改状态为 ST_CANCELLED，会在下次 tick 时，在格子中移除
            if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
                return false;
            }
            //加入到时间轮的待取消队列，并在每次 tick 的时候，从相应格子中移除
            //因此，这意味着我们将有最大的 GC 延迟。1 tick 时间足够好。这样我们可以再次使用我们的 MpscLinkedQueue 队列，尽可能减少锁定/开销。
            try {
                timer.cancelledTimeouts.put(this);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return true;
        }

        /**
         * 比较并设置状态
         *
         * @param expected 预期值--原值
         * @param state    更新的值
         * @return
         */
        public boolean compareAndSetState(int expected, int state) {
            return STATE_UPDATER.compareAndSet(this, expected, state);
        }

        public int state() {
            return state;
        }

        @Override
        public boolean isExpired() {
            return state() == ST_EXPIRED;
        }

        @Override
        public boolean isCancelled() {
            return state() == ST_CANCELLED;
        }


        //从格子中移除自身
        void remove() {
            HashedWheelBucket bucket = this.bucket;
            if (bucket != null) {
                bucket.remove(this);
            } else {
                //计数器减一
                timer.pendingTimeouts.decrementAndGet();
            }
        }

        //过期并执行任务
        public void expire() {
            if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
                return;
            }

            try {
                task.run(this);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("An exception was thrown by " + TimerTask.class.getSimpleName() + '.', t);
                }
            }
        }


        @Override
        public String toString() {
            final long currentTime = System.nanoTime();
            long remaining = deadline - currentTime + timer.startTime;

            StringBuilder buf = new StringBuilder(192)
                    .append(simpleClassName(this))
                    .append('(');

            buf.append("remainingRounds:" + remainingRounds);
            buf.append(", deadline: ");
            if (remaining > 0) {
                buf.append(remaining)
                        .append(" ns later");
            } else if (remaining < 0) {
                buf.append(-remaining)
                        .append(" ns ago");
            } else {
                buf.append("now");
            }

            if (isCancelled()) {
                buf.append(", cancelled");
            }
            return buf.append(", task: ")
                    .append(simpleClassName(task()))
                    .append(')')
                    .toString();
        }

    } //HashedWheelTimeout end


}
