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
package com.gettyio.core.channel.internal;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.nio.channels.CompletionHandler;

/**
 * AIO 异步读完成回调处理器。
 * <p>
 * 当异步读操作完成时，将结果转发给 {@link AioChannel#readFromChannel(boolean)}。
 * 读取失败或连接断开时自动关闭通道。
 * </p>
 *
 * @author gogym
 */
public class ReadCompletionHandler implements CompletionHandler<Integer, AioChannel> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ReadCompletionHandler.class);

    @Override
    public void completed(Integer result, AioChannel aioChannel) {
        // result == -1 表示对端已关闭连接（EOF）
        aioChannel.readFromChannel(result == -1);
    }

    @Override
    public void failed(Throwable exc, AioChannel aioChannel) {
        try {
            aioChannel.close();
        } catch (Exception e) {
            LOGGER.error("close channel failed in ReadCompletionHandler", e);
        }
    }
}
