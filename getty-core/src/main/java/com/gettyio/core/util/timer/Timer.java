package com.gettyio.core.util.timer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 时间轮接口
 * * 参考自netty 4.3
 */
public interface Timer {


    /**
     * newTimeout()添加定时任务
     * 如果没有启动时间轮，则启动
     *
     * @param task  定时任务
     * @param delay 延迟时间
     * @param unit  延迟时间单位
     * @return the timeout
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);


    /**
     * 停止时间轮
     *
     * @return the set
     */
    Set<Timeout> stop();


    /**
     * 动态开关开启
     * 1、轮子每 tick ，将格子内所有定时任务执行
     * 2、开关开启后的定时任务直接执行，不进入格子。
     */
    void openSwitch();

    /**
     * 动态开关关闭
     * 1、轮子每 tick ，只执行过期的定时任务
     * 2、新的 newTimeout 添加的定时任务，添加到格子
     */
    void closeSwitch();
}
