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
package com.gettyio.core.channel.loop;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * AIO 写线程组，管理多个 {@link AioWriteThread}。
 * <p>
 * 类似 Netty 的 EventLoopGroup，通过轮询（round-robin）将 Channel 均匀分配到各写线程。
 * 每个写线程可管理多个 Channel，共享 PoolThreadCache，显著提升高并发场景下的资源利用率。
 * </p>
 *
 * <pre>
 * 使用示例：
 *   AioWriteThreadGroup group = new AioWriteThreadGroup(4);
 *   AioWriteThread writeThread = group.next(); // 轮询获取
 *   writeThread.register(channel);
 * </pre>
 *
 * @author gogym
 * @see AioWriteThread
 */
public class AioWriteThreadGroup {

    /** 写线程数组 */
    private final AioWriteThread[] threads;

    /** 轮询分配索引 */
    private final AtomicInteger idx = new AtomicInteger(0);

    /**
     * 创建写线程组并启动所有写线程。
     *
     * @param size 写线程数量
     */
    public AioWriteThreadGroup(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("writeThreadNum must be > 0, got: " + size);
        }
        this.threads = new AioWriteThread[size];
        for (int i = 0; i < size; i++) {
            threads[i] = new AioWriteThread("aio-write-" + i);
            threads[i].start();
        }
    }

    /**
     * 轮询获取下一个写线程。
     * <p>
     * 线程安全，多个调用者可并发使用。
     * </p>
     *
     * @return 分配的写线程
     */
    public AioWriteThread next() {
        int index = Math.abs(idx.getAndIncrement() % threads.length);
        return threads[index];
    }

    /**
     * 关闭所有写线程。
     */
    public void shutdown() {
        for (AioWriteThread thread : threads) {
            thread.shutdown();
        }
    }

    /**
     * 获取写线程数量。
     */
    public int size() {
        return threads.length;
    }
}
