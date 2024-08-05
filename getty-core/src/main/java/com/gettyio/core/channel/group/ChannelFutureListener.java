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
 * ChannelFutureListener.java
 *
 * 监听，目前用于监听通道关闭时清理相关资源
 * @author:gogym
 * @date:2020/4/8
 */
public interface ChannelFutureListener {

    /**
     * 操作完成监听
     *
     * @param abstractSocketChannel
     */
    void operationComplete(AbstractSocketChannel abstractSocketChannel);

}
