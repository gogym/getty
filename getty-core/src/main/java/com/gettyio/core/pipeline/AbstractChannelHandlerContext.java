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
 * 抽象ChannelHandlerContext
 * 用于流转责任链的执行者
 */
abstract class AbstractChannelHandlerContext implements ChannelHandlerContext {

    /**
     * 下一个
     */
    volatile AbstractChannelHandlerContext next;
    /**
     * 上一个
     */
    volatile AbstractChannelHandlerContext prev;


    /**
     * 根据当前通道状态处理事件
     *
     * 本方法作为通道事件处理函数，根据不同的通道状态执行相应的处理逻辑
     * 当通道状态为新建、关闭、读取、事件、异常时，如果存在下一个处理器，则调用下一个处理器处理事件
     * 当通道状态为写入时，如果存在前一个处理器，则调用前一个处理器处理事件
     *
     * @param channelState 通道的当前状态
     * @param in 通道事件的数据
     * @throws Exception 如果处理过程中发生错误，则抛出异常
     */
    @Override
    public void fireChannelProcess(ChannelState channelState, Object in) throws Exception {
        // 根据通道状态进行事件处理
        switch (channelState) {
            // 当通道状态为新建、关闭、读取、事件、异常时，调用下一个处理器处理事件
            case NEW_CHANNEL:
            case CHANNEL_CLOSED:
            case CHANNEL_READ:
            case CHANNEL_EVENT:
            case CHANNEL_EXCEPTION:
                // 如果存在下一个处理器，则调用下一个处理器处理事件
                if (next != null) {
                    next.invokeChannelProcess(channelState, in);
                }
                break;
            // 当通道状态为写入时，调用前一个处理器处理事件
            case CHANNEL_WRITE:
                // 如果存在前一个处理器，则调用前一个处理器处理事件
                if (prev != null) {
                    prev.invokeChannelProcess(channelState, in);
                }
                break;
        }
    }


    /**
     * 调用通道处理方法
     *
     * 此方法封装了handler的channelProcess方法调用，提供了一个统一的入口
     * 用于处理不同的通道状态和输入数据。它简化了对处理程序的直接调用，
     * 并允许我们在未来更容易地进行修改和扩展。
     *
     * @param channelState 通道的状态对象，指示当前通道的状态
     * @param in 输入参数，可以是任何类型的对象，用于通道处理
     * @throws Exception 如果处理过程中发生错误，将抛出异常
     */
    private void invokeChannelProcess(ChannelState channelState, Object in) throws Exception {
        handler().channelProcess(this, channelState, in);
    }



}
