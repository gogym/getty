//package test;
//
//import java.util.Deque;
//import java.util.Iterator;
//import java.util.LinkedList;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicLong;
//
///**
// * @author gogym
// * $
// */
//public class HashedWheelTimer {
//    // 定时轮的大小，默认为512
//    private final int wheelSize=512;
//    // TickDuration 表示一个tick的时间间隔，默认为100毫秒
//    private final long tickDuration;
//    // 保存时间轮上的所有槽
//    private final HashedWheelBucket[] wheel;
//    // 与时间相关的戳，表示当前需要指向的槽位
//    private final AtomicLong currentTime;
//    // 时间轮启动的线程
//    private final Thread workerThread;
//
//    public HashedWheelTimer(int tickDuration, TimeUnit unit, int wheelSize) {
//        // 初始化时间轮的大小
//        if (wheelSize <= 0) {
//            throw new IllegalArgumentException("Wheel size must be greater than 0");
//        }
//        // 初始化时间轮每一个刻度的时间间隔
//        this.tickDuration = unit.toMillis(tickDuration);
//        // 初始化时间轮的槽数组
//        this.wheel = createWheel(wheelSize);
//        // 初始化当前时间为0
//        this.currentTime = new AtomicLong(0);
//        // 初始化时间轮的线程
//        this.workerThread = new Thread(new Worker(), "HashedWheelTimerThread");
//        this.workerThread.start();
//    }
//
//    // 创建时间轮的槽数组
//    private HashedWheelBucket[] createWheel(int wheelSize) {
//        HashedWheelBucket[] wheel = new HashedWheelBucket[wheelSize];
//        for (int i = 0; i < wheel.length; i++) {
//            wheel[i] = new HashedWheelBucket();
//        }
//        return wheel;
//    }
//
//    // 启动定时器，开始执行任务调度
//    public void start() {
//        if (workerThread.isAlive()) {
//            throw new IllegalStateException("HashedWheelTimer is already started");
//        }
//        workerThread.start();
//    }
//
//    // 加入一个定时任务到时间轮中
//    public TimerFuture schedule(Runnable task, long delay, TimeUnit unit) {
//        // 计算任务的触发时间
//        long deadline = System.currentTimeMillis() + unit.toMillis(delay);
//
//        // 计算任务需要等待多少个刻度
//        long ticks = delay / tickDuration;
//
//        // 将任务加入到对应的时间轮槽中
//        HashedWheelBucket bucket = wheel[(int) (currentTime.get() + ticks) & (wheel.length - 1)];
//        bucket.addTask(new TimerTask(task, deadline, ticks));
//
//        // 返回一个任务结果，用于取消任务
//        return new TimerFuture(bucket, new TimerTask(task, deadline, ticks));
//    }
//
//    // 时间轮的工作线程
//    private class Worker implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                // 计算当前时间
//                long currentTime = System.currentTimeMillis();
//
//                // 更新时间轮的当前时间
//                HashedWheelTimer.this.currentTime.set(currentTime);
//
//                // 获取当前时间对应的时间轮槽位
//                int index = (int) (currentTime & (wheel.length - 1));
//                HashedWheelBucket bucket = wheel[index];
//
//                // 执行时间轮槽中的任务
//                bucket.expireTasks(currentTime);
//
//                // 等待下一个tick
//                waitForNextTick();
//            }
//        }
//
//        // 等待下一个tick
//        private void waitForNextTick() {
//            try {
//                // 通过Thread.sleep()方法等待下一个tick的到来
//                Thread.sleep(tickDuration);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }
//    }
//}
//
//// 时间轮中的槽位
//class HashedWheelBucket {
//    private final Deque<TimerTask> tasks;
//
//    HashedWheelBucket() {
//        tasks = new LinkedList<>();
//    }
//
//    // 添加一个任务到槽位
//    void addTask(TimerTask task) {
//        tasks.add(task);
//    }
//
//    // 触发并执行槽位中的任务
//    void expireTasks(long currentTime) {
//        Iterator<TimerTask> iterator = tasks.iterator();
//        while (iterator.hasNext()) {
//            TimerTask task = iterator.next();
//            // 判断任务是否已经过期
//            if (task.getDeadline() <= currentTime) {
//                // 执行任务
//                task.getTask().run();
//                // 从槽位中移除任务
//                iterator.remove();
//            }
//        }
//    }
//
//    public Deque<TimerTask> getTasks() {
//        return tasks;
//    }
//}
//
//// 定时任务
//class TimerTask {
//    private final Runnable task;
//    private final long deadline;
//    private final long ticks;
//
//    TimerTask(Runnable task, long deadline, long ticks) {
//        this.task = task;
//        this.deadline = deadline;
//        this.ticks = ticks;
//    }
//
//    Runnable getTask() {
//        return task;
//    }
//
//    long getDeadline() {
//        return deadline;
//    }
//
//    long getTicks() {
//        return ticks;
//    }
//}
//
//// 任务结果
//class TimerFuture {
//    private final HashedWheelBucket bucket;
//    private final TimerTask task;
//
//    TimerFuture(HashedWheelBucket bucket, TimerTask task) {
//        this.bucket = bucket;
//        this.task = task;
//    }
//
//    // 取消任务
//    void cancel() {
//        bucket.getTasks().remove(task);
//    }
//}
