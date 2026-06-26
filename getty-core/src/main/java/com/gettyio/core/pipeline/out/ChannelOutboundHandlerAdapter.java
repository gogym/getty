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
package com.gettyio.core.pipeline.out;

import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * 出站处理器抽象基类。
 * <p>
 * 需要处理入站事件（连接建立、数据读取、连接关闭、异常）的处理器应继承此类。
 * 继承 {@link ChannelHandlerAdapter}，对所有事件提供默认的透传行为，
 * 子类只需覆盖感兴趣的方法。
 * </p>
 *
 * <p>对于简单的消息接收场景，推荐使用 {@link SimpleChannelInboundHandler}，
 * 它提供了泛型化的消息处理方法。</p>
 */
public abstract class ChannelOutboundHandlerAdapter extends ChannelHandlerAdapter {
}
