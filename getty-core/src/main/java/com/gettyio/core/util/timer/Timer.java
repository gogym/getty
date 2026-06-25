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
 * 定时器接口。
 * <p>
 * 定义定时任务的提交、停止和动态开关操作。
 * 典型实现为 {@link HashedWheelTimer}（时间轮算法）。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public interface Timer {

    /**
     * 提交一个定时任务。
     * <p>
     * 如果定时器未启动，将自动启动。
     * </p>
     *
     * @param task  定时任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return 超时句柄，可用于取消或查询状态
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    /**
     * 停止定时器
     *
     * @return 未处理的超时任务集合
     */
    Set<Timeout> stop();

    /**
     * 开启动态开关。
     * <p>
     * 开启后：
     * <ul>
     *   <li>每次 tick 时执行格子内所有定时任务（不论是否到期）</li>
     *   <li>新提交的任务直接执行，不进入格子</li>
     * </ul>
     * </p>
     */
    void openSwitch();

    /**
     * 关闭动态开关。
     * <p>
     * 关闭后：
     * <ul>
     *   <li>每次 tick 只执行已过期的定时任务</li>
     *   <li>新提交的任务正常进入格子等待</li>
     * </ul>
     * </p>
     */
    void closeSwitch();
}
