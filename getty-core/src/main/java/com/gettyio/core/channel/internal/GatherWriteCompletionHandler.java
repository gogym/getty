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
 * AIO 异步 Gathering Write 完成回调处理器。
 * <p>
 * 当 {@link java.nio.channels.AsynchronousSocketChannel#write(ByteBuffer[], int, int, long, java.util.concurrent.TimeUnit, Object, CompletionHandler)}
 * 完成时回调。结果类型为 {@link Long}（本次写出的总字节数）。
 * </p>
 *
 * @author gogym
 */
public class GatherWriteCompletionHandler implements CompletionHandler<Long, AioChannel> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(GatherWriteCompletionHandler.class);

    @Override
    public void completed(Long result, AioChannel aioChannel) {
        try {
            aioChannel.writeCompleted();
        } catch (Exception e) {
            failed(e, aioChannel);
        }
    }

    @Override
    public void failed(Throwable exc, AioChannel aioChannel) {
        try {
            aioChannel.close();
        } catch (Exception e) {
            LOGGER.error("close channel failed in GatherWriteCompletionHandler", e);
        }
    }
}
