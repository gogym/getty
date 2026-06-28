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
 * 处理器上下文抽象基类。
 * <p>
 * 实现双向链表结构，并根据事件类型决定传播方向：
 * <ul>
 *   <li><b>入站事件</b>（NEW_CHANNEL / CHANNEL_CLOSED / CHANNEL_READ / CHANNEL_EVENT / CHANNEL_EXCEPTION）：
 *       沿 next 方向向尾传播</li>
 *   <li><b>出站事件</b>（CHANNEL_WRITE）：沿 prev 方向向头传播，
 *       到达头节点时自动调用 {@code channel().writeToSocket(obj)} 写入底层通道</li>
 * </ul>
 * </p>
 *
 * <p><b>性能优化说明：</b>写路径在 {@link #fireChannelProcess} 中直接检查 {@code prev == null}
 * 来判断是否到达头节点，避免了原先每次写操作都要调用 {@code pipeline.isFirst(this)}
 * 进行链表遍历的开销。这是管道写路径的核心热路径优化。</p>
 */
abstract class AbstractChannelHandlerContext implements ChannelHandlerContext {

    /** 链表后继节点（入站方向下一个处理器） */
    volatile AbstractChannelHandlerContext next;

    /** 链表前驱节点（出站方向上一个处理器） */
    volatile AbstractChannelHandlerContext prev;

    /**
     * 将事件传播到下一个处理器。
     * <p>
     * 根据事件类型选择传播方向：
     * <ul>
     *   <li>CHANNEL_WRITE：向前（prev）传播，到达 head 哨兵时直接写入通道</li>
     *   <li>其他入站事件：向后（next）传播</li>
     * </ul>
     * </p>
     *
     * @param channelState 通道状态
     * @param in           事件数据
     * @throws Exception 传播过程中发生错误时抛出
     */
    @Override
    public void fireChannelProcess(ChannelState channelState, Object in) throws Exception {
        if (channelState == ChannelState.CHANNEL_WRITE) {
            // 写路径：向前（prev）传播
            AbstractChannelHandlerContext p = prev;
            if (p == null) {
                // 已到达头哨兵，直接写入底层通道（核心性能优化：内联 isFirst 检查，
                // 避免每次写操作都遍历链表调用 pipeline.isFirst()）
                channel().writeToSocket(in);
            } else {
                p.invokeChannelProcess(channelState, in);
            }
        } else {
            // 入站路径：向后（next）传播
            AbstractChannelHandlerContext n = next;
            if (n != null) {
                n.invokeChannelProcess(channelState, in);
            }
        }
    }

    /**
     * 调用绑定的处理器处理方法。
     * <p>
     * 标记为 final 以允许 JIT 内联优化，这是管道事件分发的热路径。
     * </p>
     *
     * @param channelState 通道状态
     * @param in           事件数据
     * @throws Exception 处理过程中发生错误时抛出
     */
    private void invokeChannelProcess(ChannelState channelState, Object in) throws Exception {
        handler().channelProcess(this, channelState, in);
    }
}
