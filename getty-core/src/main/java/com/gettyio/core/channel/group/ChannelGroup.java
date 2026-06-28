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

import java.util.Set;

/**
 * 通道分组接口。
 * <p>
 * 用于管理一组通道，方便批量操作（如广播消息）和按 ID 查找通道。
 * 继承 {@link Set} 提供集合操作，继承 {@link Comparable} 支持按名称排序。
 * </p>
 *
 * @author gogym
 */
public interface ChannelGroup extends Set<AbstractSocketChannel>, Comparable<ChannelGroup> {

    /**
     * 获取组名称。
     *
     * @return 组名称
     */
    String name();

    /**
     * 按 ID 查找通道。
     *
     * @param id 通道 ID
     * @return 匹配的通道，未找到返回 null
     */
    AbstractSocketChannel find(String id);

    /**
     * 向组内所有通道广播消息。
     * <p>
     * 对每个通道调用 {@link AbstractSocketChannel#writeAndFlush(Object)}，
     * 单个通道失败不影响其他通道的广播。
     * </p>
     *
     * @param msg 待广播的消息
     */
    void writeToAll(Object msg);
}
