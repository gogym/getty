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
 * ChannelPipeline
 * 责任链
 */
public interface ChannelPipeline {

    /**
     * 添加一个处理器到第一位
     *
     * @param handler
     * @return
     */
    ChannelPipeline addFirst(ChannelHandler handler);

    /**
     * 添加一个处理器到尾部
     *
     * @param handler
     * @return
     */
    ChannelPipeline addLast(ChannelHandler handler);

    /**
     * 获取头部
     *
     * @return
     */
    ChannelHandlerContext head();

    /**
     * 获取尾部
     *
     * @return
     */
    ChannelHandlerContext tail();

    /**
     * 判断是否是第一位处理器
     *
     * @param handler
     * @return
     */
    boolean isFirst(ChannelHandler handler);

    /**
     * 判断是否是最后一位处理器
     *
     * @param handler
     * @return
     */
    boolean isLast(ChannelHandler handler);

}
