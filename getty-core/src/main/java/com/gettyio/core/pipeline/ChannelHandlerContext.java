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

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.ChannelState;

/**
 * 处理器上下文接口。
 * <p>
 * 每个处理器在管道中都有一个对应的上下文实例，通过上下文可以：
 * <ul>
 *   <li>访问底层通道（{@link #channel()}）</li>
 *   <li>获取绑定的处理器（{@link #handler()}）</li>
 *   <li>将事件传播到下一个处理器（{@link #fireChannelProcess}）</li>
 * </ul>
 * </p>
 */
public interface ChannelHandlerContext {

    /**
     * 获取底层通道。
     *
     * @return 通道实例
     */
    AbstractSocketChannel channel();

    /**
     * 获取绑定的处理器。
     *
     * @return 处理器实例
     */
    ChannelHandler handler();

    /**
     * 将事件传播到管道中的下一个处理器。
     * <p>
     * 入站事件（读取、连接、关闭、异常、心跳）向后传播（next）；
     * 出站事件（写入）向前传播（prev）。
     * </p>
     *
     * @param channelState 通道状态
     * @param in           事件数据
     * @throws Exception 传播过程中发生错误时抛出
     */
    void fireChannelProcess(ChannelState channelState, Object in) throws Exception;
}
