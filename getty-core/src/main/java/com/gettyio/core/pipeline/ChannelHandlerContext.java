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
package com.gettyio.core.pipeline;

import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.SocketChannel;

/**
 * ChannelHandlerContext
 * 执行者上下文，用于激发流程
 */
public interface ChannelHandlerContext {

    /**
     * 获取通道
     * @return
     */
    SocketChannel channel();

    /**
     * 获取处理者
     *
     * @return
     */
    ChannelHandler handler();

    /**
     * 激发正常流转
     *
     * @param in
     * @return
     */
    void fireChannelProcess(ChannelState channelState, Object in) throws Exception;


}
