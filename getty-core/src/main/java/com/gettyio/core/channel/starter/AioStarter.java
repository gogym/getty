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
package com.gettyio.core.channel.starter;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.nio.channels.AsynchronousChannelGroup;

/**
 * AioStarter.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public abstract class AioStarter extends Starter {

    /**
     * 内存池
     */
    protected ChunkPool chunkPool;

    /**
     * 线程池
     */
    protected ThreadPool workerThreadPool;

    /**
     * aio线程组
     */
    protected AsynchronousChannelGroup asynchronousChannelGroup;

    /**
     * 责任链对象
     */
    protected ChannelPipeline channelPipeline;
}
