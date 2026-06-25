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
 * 默认空处理器。
 * <p>
 * 用作管道 head 和 tail 哨兵节点的处理器。继承 {@link ChannelHandlerAdapter}，
 * 对所有事件均执行默认的透传行为，不参与任何业务处理。
 * </p>
 */
public class DefaultChannelHandler extends ChannelHandlerAdapter {
}
