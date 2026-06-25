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
package com.gettyio.core.pipeline.all;

import com.gettyio.core.pipeline.ChannelHandlerAdapter;

/**
 * 双向（入站+出站）处理器抽象基类。
 * <p>
 * 需要同时处理入站事件（读取、连接建立、关闭、异常）和出站事件（写入）的处理器
 * 应继承此类。功能上与 {@link ChannelHandlerAdapter} 完全等价，保留此类仅为了
 * 在语义上区分"双向处理器"和"通用处理器"。
 * </p>
 *
 * <p>典型用途：SSL 处理器（需同时加密出站和解密入站）、心跳处理器（需同时监听读写活动）。</p>
 */
public abstract class ChannelAllBoundHandlerAdapter extends ChannelHandlerAdapter {
}
