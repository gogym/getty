package com.gettyio.core.util.timer;

import java.util.concurrent.TimeUnit;

/**
 * 定时任务
 */
public interface TimerTask {


    /**
     * 延时执行定时任务 {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
     *
     * @param timeout
     */
    void run(Timeout timeout) throws Exception;

}
