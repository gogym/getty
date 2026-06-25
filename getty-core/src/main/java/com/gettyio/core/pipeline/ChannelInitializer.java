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

/**
 * 管道初始化器接口。
 * <p>
 * 在新连接建立时由框架调用，用于向通道的管道中添加处理器。
 * 用户应实现此接口并在服务端/客户端启动时传入，以配置管道的处理器链。
 * </p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * serverStarter.channelInitializer(channel -> {
 *     channel.getChannelPipeline()
 *         .addLast(new MyDecoder())
 *         .addLast(new MyEncoder())
 *         .addLast(new MyBusinessHandler());
 * });
 * }</pre>
 */
public interface ChannelInitializer {

    /**
     * 初始化通道管道。在新连接建立后由框架调用。
     *
     * @param channel 新建立的通道
     * @throws Exception 初始化过程中发生错误时抛出
     */
    void initChannel(AbstractSocketChannel channel) throws Exception;
}
