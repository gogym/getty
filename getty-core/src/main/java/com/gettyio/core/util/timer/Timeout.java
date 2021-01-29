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

/**
 * Timeout.java
 *
 * @description:参考自netty 4.3
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
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
