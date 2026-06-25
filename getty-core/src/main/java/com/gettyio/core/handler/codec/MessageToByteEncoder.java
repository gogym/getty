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
package com.gettyio.core.handler.codec;

import com.gettyio.core.pipeline.ChannelHandlerAdapter;

/**
 * 消息到字节的编码器基类。
 * <p>
 * 作为出站处理器的语义标记基类，子类继承后在管道中承担"将高层消息对象编码为字节流"
 * 的职责。具体的编码逻辑（如序列化、帧封装）由子类实现。
 * </p>
 *
 * <p><b>典型子类：</b>字符串编码器、Protobuf 编码器、MQTT 编码器、HTTP 响应编码器等。</p>
 */
public abstract class MessageToByteEncoder extends ChannelHandlerAdapter {
}
