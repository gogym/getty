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
package com.gettyio.core.channel;


/**
 * ChannelState.java
 *
 * @description:通道状态
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public enum ChannelState {
    /**
     * 新的连接
     */
    NEW_CHANNEL,
    /**
     * 连接关闭。
     */
    CHANNEL_CLOSED,
    /**
     * 读取数据
     */
    CHANNEL_READ,
    /**
     * 写数据
     */
    CHANNEL_WRITE,
    /**
     * IdleState
     */
    CHANNEL_EVENT,
    /**
     * 操作异常。
     */
    CHANNEL_EXCEPTION
}
