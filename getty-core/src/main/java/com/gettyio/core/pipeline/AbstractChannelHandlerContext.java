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


    @Override
    public void fireChannelProcess(ChannelState channelState, Object in) throws Exception {
        switch (channelState) {
            case NEW_CHANNEL:
            case CHANNEL_CLOSED:
            case CHANNEL_READ:
            case CHANNEL_EVENT:
            case CHANNEL_EXCEPTION:
                if (next != null) {
                    next.invokeChannelProcess(channelState, in);
                }
                break;
            case CHANNEL_WRITE:
                if (prev != null) {
                    prev.invokeChannelProcess(channelState, in);
                }
                break;
        }
    }

    private void invokeChannelProcess(ChannelState channelState, Object in) throws Exception {
        handler().channelProcess(this, channelState, in);
    }


}
