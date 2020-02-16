package com.gettyio.core.util.timer;

/**
 * {@link Timer} 处理  {@link TimerTask} 完成, 返回值
 */
public interface Timeout {

    /**
     * 获取时间轮引用
     *
     * @return the timer
     */
    Timer timer();

    /**
     * 获取需要执行的任务
     *
     * @return the timer task
     */
    TimerTask task();

    /**
     * 定时任务是否过期
     *
     * @return the boolean
     */
    boolean isExpired();

    /**
     * 定时任务是否取消
     *
     * @return the boolean
     */
    boolean isCancelled();

    /**
     * 试图取消 {@link TimerTask}
     * 如果任务已经执行或取消，它将直接返回
     *
     * @return 如果取消成功，则返回true
     */
    boolean cancel();

}
