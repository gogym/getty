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

import com.gettyio.core.constant.IdleState;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;

/**
 * 心跳超时处理器。
 * <p>
 * 配合 {@link IdleStateHandler} 使用，当检测到连续多次读空闲事件时，
 * 判定对端无响应并关闭连接。收到有效数据时重置计数器。
 * </p>
 *
 * @author gogym
 * @see IdleStateHandler
 */
public class HeartBeatTimeOutHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(HeartBeatTimeOutHandler.class);

    /** 默认最大允许空闲次数 */
    private static final int DEFAULT_MAX_IDLE_COUNT = 3;

    /** 连续空闲计数 */
    private volatile int lossConnectCount = 0;

    /** 最大允许空闲次数，超过则关闭连接 */
    private final int maxIdleCount;

    /**
     * 使用默认最大空闲次数（3次）创建心跳超时处理器。
     */
    public HeartBeatTimeOutHandler() {
        this(DEFAULT_MAX_IDLE_COUNT);
    }

    /**
     * 使用指定最大空闲次数创建心跳超时处理器。
     *
     * @param maxIdleCount 最大允许连续空闲次数，超过则关闭连接
     */
    public HeartBeatTimeOutHandler(int maxIdleCount) {
        if (maxIdleCount <= 0) {
            throw new IllegalArgumentException("maxIdleCount must be positive: " + maxIdleCount);
        }
        this.maxIdleCount = maxIdleCount;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) throws Exception {
        if (evt == IdleState.READER_IDLE) {
            lossConnectCount++;
            if (lossConnectCount >= maxIdleCount) {
                LOGGER.info("[closed inactive channel: {}]", ctx.channel().getRemoteAddress().getHostString());
                ctx.channel().close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        lossConnectCount = 0;
        super.channelRead(ctx, in);
    }
}
