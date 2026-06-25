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

/**
 * 管道接口，处理器链的管理器。
 * <p>
 * 管道内部维护一个双向链表结构的处理器链：
 * <ul>
 *   <li><b>head</b>：哨兵头节点，不参与业务处理，是入站事件的起点</li>
 *   <li><b>tail</b>：哨兵尾节点，不参与业务处理，是出站事件的起点</li>
 * </ul>
 * 事件传播方向：
 * <ul>
 *   <li>入站事件（读、连接、关闭、异常、心跳）：head → ... → tail（next 方向）</li>
 *   <li>出站事件（写）：tail → ... → head（prev 方向），到达第一个处理器时写入底层通道</li>
 * </ul>
 * </p>
 */
public interface ChannelPipeline {

    /**
     * 在管道头部（紧随 head 哨兵之后）插入处理器。
     *
     * @param handler 要添加的处理器
     * @return 当前管道，支持链式调用
     */
    ChannelPipeline addFirst(ChannelHandler handler);

    /**
     * 在管道尾部（紧随 tail 哨兵之前）追加处理器。
     *
     * @param handler 要添加的处理器
     * @return 当前管道，支持链式调用
     */
    ChannelPipeline addLast(ChannelHandler handler);

    /**
     * 获取头哨兵节点。
     *
     * @return 头节点上下文
     */
    ChannelHandlerContext head();

    /**
     * 获取尾哨兵节点。
     *
     * @return 尾节点上下文
     */
    ChannelHandlerContext tail();
}
