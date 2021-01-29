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

import java.io.IOException;
import java.io.OutputStream;


/**
 * 流数据统一输出
 *
 * @author gogym
 * @version 1.0.0
 * @className AbstractBufferWriter.java
 * @description
 * @date 2020/6/17
 */
public abstract class AbstractBufferWriter<T> extends OutputStream {

    /**
     * 内存池
     */
    ChunkPool chunkPool;
    /**
     * 内存申请最大阻塞时间
     */
    int chunkPoolBlockTime;
    /**
     * 当前是否已关闭
     */
    boolean closed = false;

    @Override
    public void write(int b) throws IOException {
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
    }

    /**
     * 写入队列，并刷新
     *
     * @param b
     * @throws IOException 可能会有IO异常
     */
    public void writeAndFlush(byte[] b) throws IOException {
    }

    /**
     * 是否已经关闭
     *
     * @return
     */
    public abstract boolean isClosed();

    /**
     * 弹出队列中的数据
     *
     * @return
     */
    public abstract T poll();

}
