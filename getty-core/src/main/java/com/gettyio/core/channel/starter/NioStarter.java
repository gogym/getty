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

package com.gettyio.core.channel.starter;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

/**
 * NioStarter.java
 *
 * @description:
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public abstract class NioStarter extends Starter {

    /**
     * 开启的socket模式 TCP/UDP ,默认tcp
     */
    protected SocketMode socketMode = SocketMode.TCP;

    /**
     * 内存池
     */
    protected ChunkPool chunkPool;

    /**
     * 责任链对象
     */
    protected ChannelPipeline channelPipeline;
}
