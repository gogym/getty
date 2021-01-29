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
package com.gettyio.core.channel.starter;


import com.gettyio.core.util.ThreadPool;

/**
 * 类名：Starter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/4/8
 */
public abstract class Starter {

    /**
     * Boss线程数，获取cpu核心,核心小于4设置线程为3，大于4设置和cpu核心数一致
     */
    protected int bossThreadNum = Runtime.getRuntime().availableProcessors() < 4 ? 3 : Runtime.getRuntime().availableProcessors();
    /**
     * Boss共享给Worker的线程数，核心小于4设置线程为1，大于4右移两位
     */
    private int bossShareToWorkerThreadNum = bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2;
    /**
     * Worker线程数
     */
    protected int workerThreadNum = bossThreadNum - bossShareToWorkerThreadNum;


    /**
     * boss线程池
     */
    protected ThreadPool bossThreadPool;

    /**
     * 线程池
     */
    protected ThreadPool workerThreadPool;

}
