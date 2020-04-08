/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.channel.internal;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.ThreadPool;

import java.nio.channels.CompletionHandler;

/**
 * ReadCompletionHandler.java
 *
 * @description:读回调事件
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public class ReadCompletionHandler implements CompletionHandler<Integer, AioChannel> {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ReadCompletionHandler.class);

    /**
     * 线程池
     */
    private ThreadPool executorService;

    public ReadCompletionHandler(ThreadPool executorService) {
        this.executorService = executorService;
    }

    @Override
    public void completed(final Integer result, final AioChannel aioChannel) {
        //通过多线程形式读取，提高处理效率
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                aioChannel.readFromChannel(result == -1);
            }
        });
    }

    @Override
    public void failed(Throwable exc, AioChannel aioChannel) {
        try {
            aioChannel.close();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

    }
}