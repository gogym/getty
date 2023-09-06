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
package com.gettyio.core.pipeline.out;

import com.gettyio.core.pipeline.ChannelBoundHandler;
import com.gettyio.core.pipeline.ChannelHandlerContext;

/**
 * ChannelOutboundHandler.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface ChannelOutboundHandler extends ChannelBoundHandler {


    /**
     * 消息写出
     *
     * @param ctx 通道
     * @param obj 数据
     * @throws Exception 异常
     */
    @Override
    void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception;

}
