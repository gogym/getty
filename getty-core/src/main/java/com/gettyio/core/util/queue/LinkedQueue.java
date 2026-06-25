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
package com.gettyio.core.util.queue;

/**
 * 自定义队列接口。
 * <p>
 * 基于数组环形缓冲区实现，提供 put/poll/take 三种操作语义：
 * <ul>
 *   <li>{@link #put(Object)} — 入队，队列满时阻塞</li>
 *   <li>{@link #poll()} — 出队，队列空时返回 null（非阻塞）</li>
 *   <li>{@link #take()} — 出队，队列空时阻塞</li>
 * </ul>
 * </p>
 *
 * @param <T> 元素类型
 * @author gogym
 * @date 2020/4/9
 */
public interface LinkedQueue<T> {

    /**
     * 获取底层数组对象（用于批量操作场景）
     *
     * @param componentType 数组元素的 Class
     * @param length        数组长度
     * @param <T>           数组元素类型
     * @return 新创建的数组
     */
    <T> T[] getArray(Class<T> componentType, int length);

    /**
     * 入队操作。将元素加入队列尾部。
     * 队列满时阻塞等待。
     *
     * @param t 要入队的元素（不允许 null）
     * @return 入队的元素
     * @throws InterruptedException 等待时被中断
     */
    T put(T t) throws InterruptedException;

    /**
     * 非阻塞出队操作。从队列头部取出元素。
     * 队列为空时返回 {@code null}。
     *
     * @return 队头元素，队列为空时返回 {@code null}
     * @throws InterruptedException 操作时被中断
     */
    T poll() throws InterruptedException;

    /**
     * 阻塞出队操作。从队列头部取出元素。
     * 队列为空时阻塞等待。
     *
     * @return 队头元素
     * @throws InterruptedException 等待时被中断
     */
    T take() throws InterruptedException;

    /**
     * 获取当前队列中的元素数量
     *
     * @return 元素数量
     */
    int getCount();

    /**
     * 获取队列的容量上限
     *
     * @return 队列容量
     */
    int getCapacity();

    /**
     * 排空队列中的所有元素并唤醒所有等待的线程。
     * <p>
     * 用于关闭场景：释放残留资源，并通过 signalAll 唤醒
     * 因 put/take 而阻塞的线程，使其能够检测到关闭状态并退出。
     * </p>
     */
    void drain();
}
