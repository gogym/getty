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

import com.gettyio.core.buffer.pool.RetainableByteBuffer;

import java.io.IOException;

/**
 * 流数据统一输出抽象基类。
 *
 * @author gogym
 */
public abstract class AbstractBufferWriter {

    /**
     * 当前是否已关闭
     */
    boolean closed;

    /**
     * 写入字节数组
     *
     * @param b   数据
     * @param off 起始偏移
     * @param len 写入长度
     * @throws IOException 写入异常
     */
    public abstract void write(byte[] b, int off, int len) throws IOException;

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
     * 写入并刷新
     *
     * @param b 数据
     * @throws IOException 写入或刷新异常
     */
    public abstract void writeAndFlush(byte[] b) throws IOException;

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
    public abstract RetainableByteBuffer poll();

    /**
     * 获取队列中缓冲区数量
     *
     * @return 队列大小
     */
    public abstract int getCount();
}
