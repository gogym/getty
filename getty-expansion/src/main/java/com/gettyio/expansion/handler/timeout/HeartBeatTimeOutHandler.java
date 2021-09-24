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

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.constant.IdleState;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.LinkedBlockQueue;


/**
 * HeartBeatTimeOutHandler.java
 *
 * @description:心跳检测
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class HeartBeatTimeOutHandler extends ChannelInboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HeartBeatTimeOutHandler.class);
    private int loss_connect_time = 0;

    @Override
    public void userEventTriggered(SocketChannel socketChannel, IdleState evt) throws Exception {
        if (evt == IdleState.READER_IDLE) {
            loss_connect_time++;
            if (loss_connect_time > 2) {
                // 超过3次检测没有心跳就关闭这个连接
                logger.info("[closed inactive channel:" + socketChannel.getRemoteAddress().getHostString() + "]");
                socketChannel.close();
            }
        }
        super.userEventTriggered(socketChannel, evt);
    }


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedBlockQueue<Object> out) throws Exception {
        loss_connect_time = 0;
        super.decode(socketChannel, obj, out);
    }

}
