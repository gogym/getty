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
package com.gettyio.expansion.handler.traffic;

import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.thread.ThreadPool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通道级别流量统计处理器。
 * <p>
 * 统计通道读写操作的字节数和次数，并按指定间隔回调 {@link TrafficShapingHandler}，
 * 报告累计流量和周期内吞吐量。使用 {@link AtomicLong} 保证读写计数器的线程安全。
 * </p>
 *
 * @author gogym
 * @see TrafficShapingHandler
 */
public class ChannelTrafficShapingHandler extends ChannelAllBoundHandlerAdapter {

    /** 累计读取字节数 */
    private final AtomicLong totalRead = new AtomicLong();

    /** 累计写出字节数 */
    private final AtomicLong totalWrite = new AtomicLong();

    /** 当前周期读取字节数（仅在定时器线程中读写，无需 volatile） */
    private long intervalTotalRead;

    /** 当前周期写出字节数（仅在定时器线程中读写，无需 volatile） */
    private long intervalTotalWrite;

    /** 周期内读取字节临时累加器 */
    private final AtomicLong intervalTotalReadTmp = new AtomicLong();

    /** 周期内写出字节临时累加器 */
    private final AtomicLong intervalTotalWriteTmp = new AtomicLong();

    /** 累计读取次数 */
    private final AtomicLong totalReadCount = new AtomicLong();

    /** 累计写出次数 */
    private final AtomicLong totalWriteCount = new AtomicLong();

    /** 定时任务线程池 */
    private final ThreadPool pool;

    /**
     * 创建通道流量统计处理器。
     *
     * @param checkInterval       统计回调间隔（毫秒）
     * @param trafficShapingHandler 流量回调处理器，可为 null
     */
    public ChannelTrafficShapingHandler(int checkInterval, final TrafficShapingHandler trafficShapingHandler) {
        pool = new ThreadPool(ThreadPool.SingleThread, 1);
        pool.scheduleWithFixedRate(() -> {
            intervalTotalRead = intervalTotalReadTmp.getAndSet(0);
            intervalTotalWrite = intervalTotalWriteTmp.getAndSet(0);
            if (trafficShapingHandler != null) {
                trafficShapingHandler.callback(
                        totalRead.get(), totalWrite.get(),
                        intervalTotalRead, intervalTotalWrite,
                        totalReadCount.get(), totalWriteCount.get());
            }
        }, 0, checkInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        int len = getDataLength(in);
        totalRead.addAndGet(len);
        intervalTotalReadTmp.addAndGet(len);
        totalReadCount.incrementAndGet();
        ctx.fireChannelProcess(ChannelState.CHANNEL_READ, in);
    }

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        int len = getDataLength(obj);
        totalWrite.addAndGet(len);
        intervalTotalWriteTmp.addAndGet(len);
        totalWriteCount.incrementAndGet();
        ctx.fireChannelProcess(ChannelState.CHANNEL_WRITE, obj);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        pool.shutdown();
        super.channelClosed(ctx);
    }

    /**
     * 获取数据长度。
     * <p>
     * 支持 RetainableByteBuffer 和 byte[] 两种类型。
     * </p>
     */
    private int getDataLength(Object data) {
        if (data instanceof RetainableByteBuffer) {
            return ((RetainableByteBuffer) data).readableBytes();
        } else if (data instanceof byte[]) {
            return ((byte[]) data).length;
        }
        byte[] bytes = com.gettyio.core.util.ObjectUtil.ObjToByteArray(data);
        return bytes.length;
    }

    public long getTotalRead() {
        return totalRead.get();
    }

    public long getTotalWrite() {
        return totalWrite.get();
    }

    public long getIntervalTotalRead() {
        return intervalTotalRead;
    }

    public long getIntervalTotalWrite() {
        return intervalTotalWrite;
    }

    public long getTotalReadCount() {
        return totalReadCount.get();
    }

    public long getTotalWriteCount() {
        return totalWriteCount.get();
    }
}
