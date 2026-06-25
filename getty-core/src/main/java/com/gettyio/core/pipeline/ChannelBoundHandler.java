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

import com.gettyio.core.constant.IdleState;

/**
 * 通道事件处理器接口。
 * <p>
 * 定义了通道生命周期中的所有事件回调方法。用户编写的处理器应实现此接口
 * （通常通过继承 {@link ChannelHandlerAdapter}）并按需覆盖感兴趣的方法。
 * </p>
 *
 * <p>事件流向：</p>
 * <ul>
 *   <li><b>入站事件</b>（连接建立、数据读取、连接关闭、异常、心跳）沿链表从头向尾传播</li>
 *   <li><b>出站事件</b>（数据写入）沿链表从尾向头传播</li>
 * </ul>
 */
public interface ChannelBoundHandler extends ChannelHandler {

    /**
     * 通道加入事件。当处理器被添加到管道后触发。
     *
     * @param ctx 通道上下文
     * @throws Exception 处理过程中发生错误时抛出
     */
    void channelAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * 通道关闭事件。当连接断开或主动关闭时触发。
     *
     * @param ctx 通道上下文
     * @throws Exception 处理过程中发生错误时抛出
     */
    void channelClosed(ChannelHandlerContext ctx) throws Exception;

    /**
     * 通道读取事件。当接收到数据时触发。
     *
     * @param ctx 通道上下文
     * @param in  接收到的数据
     * @throws Exception 处理过程中发生错误时抛出
     */
    void channelRead(ChannelHandlerContext ctx, Object in) throws Exception;

    /**
     * 通道写入事件。当需要向通道发送数据时触发。
     * <p>
     * 该事件沿管道反向传播（从尾到头）。第一个处理器负责将数据写入底层通道。
     * </p>
     *
     * @param ctx 通道上下文
     * @param obj 待写出的数据
     * @throws Exception 处理过程中发生错误时抛出
     */
    void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception;

    /**
     * 异常捕获事件。当通道处理过程中发生异常时触发。
     *
     * @param ctx   通道上下文
     * @param cause 异常原因
     * @throws Exception 处理过程中发生错误时抛出
     */
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * 用户事件触发。当前用于心跳检测（空闲状态事件）。
     *
     * @param ctx 通道上下文
     * @param evt 空闲状态枚举
     * @throws Exception 处理过程中发生错误时抛出
     */
    void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) throws Exception;
}
