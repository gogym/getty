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
package com.gettyio.core.util.timer;

import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * Timer.java
 *
 * @description:参考自netty 4.3
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
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
