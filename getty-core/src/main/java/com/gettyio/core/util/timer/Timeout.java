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
 * 定时任务超时句柄接口。
 * <p>
 * 代表一个已提交到 {@link Timer} 的定时任务的引用，
 * 提供状态查询和取消操作。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public interface Timeout {

    /**
     * 获取关联的定时器实例
     *
     * @return 定时器引用
     */
    Timer timer();

    /**
     * 获取关联的定时任务
     *
     * @return 定时任务
     */
    TimerTask task();

    /**
     * 判断定时任务是否已过期
     *
     * @return {@code true} 如果任务已到期执行
     */
    boolean isExpired();

    /**
     * 判断定时任务是否已取消
     *
     * @return {@code true} 如果任务已被取消
     */
    boolean isCancelled();

    /**
     * 尝试取消定时任务。
     * <p>
     * 如果任务已执行或已取消，则直接返回 {@code false}。
     * </p>
     *
     * @return {@code true} 如果成功取消
     */
    boolean cancel();
}
