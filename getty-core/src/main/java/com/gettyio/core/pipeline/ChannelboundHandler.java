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
 * ChannelBoundHandler.java
 * 所有handler方法集合
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface ChannelBoundHandler extends ChannelHandler {

    /**
     * 连接进来
     *
     * @param ctx 通道
     * @throws Exception 异常
     */
    void channelAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * 连接关闭
     *
     * @param ctx 通道
     * @throws Exception 异常
     */
    void channelClosed(ChannelHandlerContext ctx) throws Exception;

    /**
     * 消息读取
     *
     * @param ctx 通道
     * @param in  读取消息
     * @throws Exception 异常
     */
    void channelRead(ChannelHandlerContext ctx, Object in) throws Exception;


    /**
     * 消息写出
     *
     * @param ctx 通道
     * @param obj 数据
     * @throws Exception 异常
     */
    void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception;

    /**
     * 异常
     *
     * @param ctx   通道
     * @param cause 异常信息
     * @throws Exception 异常
     */
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;


    /**
     * 该方法类是一个心跳起搏器，执行读或写操作会被触发
     *
     * @param ctx 通道
     * @param evt IdleState
     * @throws Exception 异常
     */
    void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) throws Exception;

}
