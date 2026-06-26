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
 * AIO 异步写完成回调处理器。
 * <p>
 * 当 Gathering Write 操作完成时，通知 {@link AioChannel#writeCompleted()} 继续写出后续数据。
 * 写失败时自动关闭通道。
 * </p>
 *
 * @author gogym
 */
public class WriteCompletionHandler implements CompletionHandler<Long, AioChannel> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(WriteCompletionHandler.class);

    @Override
    public void completed(Long result, AioChannel aioChannel) {
        try {
            aioChannel.writeCompleted();
        } catch (Exception e) {
            // writeCompleted() 内部已部分释放缓冲区，不能调用 failed() → close()
            // 否则 close() 会重复释放已释放的缓冲区，导致 IllegalStateException
            LOGGER.error("writeCompleted failed", e);
        }
    }

    @Override
    public void failed(Throwable exc, AioChannel aioChannel) {
        try {
            aioChannel.close();
        } catch (Exception e) {
            LOGGER.error("close channel failed in WriteCompletionHandler", e);
        }
    }
}
