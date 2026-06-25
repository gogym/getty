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
package com.gettyio.core.channel.group;

import com.gettyio.core.channel.AbstractSocketChannel;

/**
 * 通道关闭监听器。用于在通道关闭时清理相关资源（如从 ChannelGroup 中移除）。
 *
 * @author gogym
 */
public interface ChannelFutureListener {

    /**
     * 操作完成回调。
     *
     * @param channel 已完成的通道
     */
    void operationComplete(AbstractSocketChannel channel);
}
