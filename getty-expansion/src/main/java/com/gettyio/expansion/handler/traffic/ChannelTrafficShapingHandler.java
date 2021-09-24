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

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.LinkedBlockQueue;
import com.gettyio.core.util.ObjectUtil;
import com.gettyio.core.util.ThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * ChannelTrafficShapingHandler.java
 *
 * @description:通道级别流量统计
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class ChannelTrafficShapingHandler extends ChannelAllBoundHandlerAdapter {

    /**
     * 总读取字节
     */
    private long totalRead;
    /**
     * 总写出字节
     */
    private long totalWrite;
    /**
     * 时间间隔内的吞吐量
     */
    private long intervalTotalRead;
    private long intervalTotalWrite;
    long intervalTotalReadTmp = 0;
    long intervalTotalWriteTmp = 0;

    /**
     * 读写次数
     */
    private long totalReadCount;
    private long totolWriteCount;

    /**
     * 线程池
     */
    ThreadPool pool;

    public ChannelTrafficShapingHandler(int checkInterval) {

        pool = new ThreadPool(ThreadPool.SingleThread, 1);
        pool.scheduleWithFixedRate(new Runnable() {
            @Override
            public void run() {
                intervalTotalRead = intervalTotalReadTmp;
                intervalTotalReadTmp = 0;
                intervalTotalWrite = intervalTotalWriteTmp;
                intervalTotalWriteTmp = 0;
            }
        }, 0, checkInterval, TimeUnit.MILLISECONDS);
    }


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedBlockQueue<Object> out) throws Exception {
        byte[] bytes = (byte[]) obj;
        totalRead += bytes.length;
        intervalTotalReadTmp += bytes.length;
        totalReadCount++;
        super.decode(socketChannel, obj, out);
    }

    @Override
    public void channelWrite(SocketChannel socketChannel, Object obj) throws Exception {
        byte[] bytes = ObjectUtil.ObjToByteArray(obj);
        totalWrite += bytes.length;
        intervalTotalWriteTmp += bytes.length;
        totolWriteCount++;
        super.channelWrite(socketChannel, obj);
    }

    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        pool.shutdown();
        super.channelClosed(socketChannel);
    }


    public long getTotalRead() {
        return totalRead;
    }

    public long getTotalWrite() {
        return totalWrite;
    }

    public long getIntervalTotalRead() {
        return intervalTotalRead;
    }

    public long getIntervalTotalWrite() {
        return intervalTotalWrite;
    }

    public long getTotalReadCount() {
        return totalReadCount;
    }

    public long getTotolWriteCount() {
        return totolWriteCount;
    }


}
