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


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.constant.IdleState;
import com.gettyio.core.util.LinkedNonReadBlockQueue;


/**
 * ChannelBoundHandler.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface ChannelBoundHandler {

    /**
     * 连接进来
     *
     * @param socketChannel 通道
     * @throws Exception 异常
     */
    void channelAdded(SocketChannel socketChannel) throws Exception;

    /**
     * 连接关闭
     *
     * @param socketChannel 通道
     * @throws Exception 异常
     */
    void channelClosed(SocketChannel socketChannel) throws Exception;

    /**
     * 消息读取
     *
     * @param obj           读取消息
     * @param socketChannel 通道
     * @throws Exception 异常
     */
    void channelRead(SocketChannel socketChannel, Object obj) throws Exception;


    /**
     * 消息写出
     *
     * @param socketChannel 通道
     * @param obj           数据
     * @throws Exception 异常
     */
    void channelWrite(SocketChannel socketChannel, Object obj) throws Exception;


    /**
     * 异常
     *
     * @param socketChannel 通道
     * @param cause         异常信息
     * @throws Exception 异常
     */
    void exceptionCaught(SocketChannel socketChannel, Throwable cause) throws Exception;

    /**
     * 消息解码
     *
     * @param socketChannel 通道
     * @param obj           消息
     * @param out           消息队列
     * @throws Exception 异常
     */
    void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception;


    /**
     * 消息编码
     *
     * @param socketChannel 通道
     * @param obj           数据
     * @throws Exception 异常
     */
    void encode(SocketChannel socketChannel, Object obj) throws Exception;

    /**
     * 该方法类似一个心态起搏器，执行读或写操作会被触发
     *
     * @param socketChannel 通道
     * @param evt           IdleState
     * @throws Exception 异常
     */
    void userEventTriggered(SocketChannel socketChannel, IdleState evt) throws Exception;

}
