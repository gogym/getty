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
package com.gettyio.expansion.handler.timeout;

import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.constant.IdleState;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.ThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * IdleStateHandler.java
 *
 * @description:起搏器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class IdleStateHandler extends ChannelAllBoundHandlerAdapter {


    boolean readerIdle = false;
    boolean writerIdle = false;

    /**
     * 线程池
     */
    ThreadPool pool;

    public IdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        this(readerIdleTimeSeconds, writerIdleTimeSeconds, TimeUnit.SECONDS);
    }

    public IdleStateHandler(long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        pool = new ThreadPool(ThreadPool.FixedThread, 3);
        if (readerIdleTime > 0) {
            pool.scheduleWithFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (readerIdle) {
                        try {
                            IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.READER_IDLE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    readerIdle = true;
                }
            }, 0, readerIdleTime, unit);
        }

        if (writerIdleTime > 0) {
            pool.scheduleWithFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (writerIdle) {
                        try {
                            IdleStateHandler.this.userEventTriggered(channelHandlerContext(), IdleState.WRITER_IDLE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    writerIdle = true;
                }
            }, 0, writerIdleTime, unit);
        }
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        readerIdle = false;
        super.channelRead(ctx,in);
    }

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        writerIdle = false;
        super.channelWrite(ctx,obj);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        pool.shutdown();
        super.channelClosed(ctx);
    }

}
