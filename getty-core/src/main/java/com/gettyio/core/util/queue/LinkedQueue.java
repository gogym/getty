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
 * LinkedQueue.java
 *
 * 自定义mq
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface LinkedQueue<T> {

    /**
     * 获取mq底层数组对象
     *
     * @param componentType
     * @param length
     * @param <T>
     * @return
     */
    <T> T[] getArray(Class<T> componentType, int length);

    /**
     * 加入一个元素
     *
     * @param t
     * @return t
     * @throws InterruptedException
     */
    T put(T t) throws InterruptedException;

    /**
     *
     *从队列获取一个元素,如果没有返回null
     * @return
     * @throws InterruptedException
     */
    T poll() throws InterruptedException;

    /**
     * 从队列获取一个元素,如果没有则阻塞
     * @return
     * @throws InterruptedException
     */
    T take() throws InterruptedException;

    /**
     * 获取当前队列的元素数量
     *
     * @return
     */
    int getCount();

    /**
     * 获取队列初始化大小
     *
     * @return
     */
    int getCapacity();

}
