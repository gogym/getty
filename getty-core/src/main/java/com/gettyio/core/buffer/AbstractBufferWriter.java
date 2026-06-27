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
package com.gettyio.core.buffer;

import com.gettyio.core.buffer.pool.PooledByteBuffer;

import java.io.IOException;
import java.util.List;

/**
 * 流数据统一输出抽象基类。
 *
 * @author gogym
 */
public abstract class AbstractBufferWriter {

    /**
     * 当前是否已关闭（volatile 保证多线程可见性）
     */
    volatile boolean closed;

    /**
     * 零拷贝写入：将 PooledByteBuffer 直接入队并刷新。
     *
     * @param byteBuf 待写出的缓冲区
     * @throws IOException 写入或刷新异常
     */
    public abstract void writeAndFlush(PooledByteBuffer byteBuf) throws IOException;


    /**
     * 零拷贝写入：将 PooledByteBuffer 直接入队
     *
     * @param byteBuf 待写出的缓冲区
     * @throws IOException 写入或刷新异常
     */
    public abstract void write(PooledByteBuffer byteBuf) throws IOException;


    /**
     * 刷新缓冲区
     *
     * @throws IOException 刷新异常
     */
    public abstract void flush() throws IOException;

    /**
     * 关闭输出流
     *
     * @throws IOException 关闭异常
     */
    public abstract void close() throws IOException;

    /**
     * 是否已经关闭
     *
     * @return true 如果已关闭
     */
    public abstract boolean isClosed();

    /**
     * 从队列中弹出一个缓冲区
     *
     * @return 缓冲区，队列为空时返回 null
     */
    public abstract PooledByteBuffer poll();

    /**
     * 从队列中批量弹出所有可用缓冲区。
     *
     * @param list 用于接收弹出元素的列表（调用方传入，避免每次分配）
     */
    public abstract void pollAll(List<PooledByteBuffer> list);

    /**
     * 从队列中批量弹出最多 maxCount 个缓冲区。
     *
     * @param list     用于接收弹出元素的列表
     * @param maxCount 最多弹出数量
     */
    public abstract void pollAll(List<PooledByteBuffer> list, int maxCount);

    /**
     * 获取队列中缓冲区数量
     *
     * @return 队列大小
     */
    public abstract int getCount();
}
