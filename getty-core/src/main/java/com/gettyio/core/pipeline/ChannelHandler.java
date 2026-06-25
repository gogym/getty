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

/**
 * 管道处理器基础接口。
 * <p>
 * 所有管道中的处理器都必须实现此接口。它定义了处理器的三个核心能力：
 * <ul>
 *   <li>绑定/获取上下文（{@link #setChannelHandlerContext} / {@link #channelHandlerContext}）</li>
 *   <li>处理通道事件（{@link #channelProcess}）</li>
 * </ul>
 * 该接口为包级可见，外部用户通过 {@link ChannelBoundHandler} 使用。
 * </p>
 */
interface ChannelHandler {

    /**
     * 绑定处理器到指定的上下文。在处理器被加入管道时由框架调用。
     *
     * @param ctx 处理器所属的上下文
     */
    void setChannelHandlerContext(ChannelHandlerContext ctx);

    /**
     * 获取处理器当前绑定的上下文。
     *
     * @return 处理器上下文，如果尚未绑定则返回 null
     */
    ChannelHandlerContext channelHandlerContext();

    /**
     * 处理通道事件。根据通道状态执行对应的业务逻辑。
     * <p>
     * 该方法是所有管道事件（连接、读取、写入、异常、关闭、心跳）的统一入口。
     * 具体分发逻辑由 {@link ChannelHandlerAdapter#channelProcess} 实现。
     * </p>
     *
     * @param ctx          当前处理器上下文
     * @param channelState 通道状态
     * @param in           事件数据，写入时为待写出对象，读取时为已接收数据
     * @throws Exception 处理过程中发生错误时抛出
     */
    void channelProcess(ChannelHandlerContext ctx, ChannelState channelState, Object in) throws Exception;
}
