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

import com.gettyio.core.channel.loop.AioWriteThreadGroup;

import java.nio.channels.AsynchronousChannelGroup;

/**
 * AIO Starter 基类。持有异步通道线程组和写线程组。
 *
 * @author gogym
 */
public abstract class AioStarter extends Starter {

    /** AIO 线程组，管理异步 I/O 操作的回调线程 */
    protected AsynchronousChannelGroup asynchronousChannelGroup;

    /** AIO 共享写线程组，管理多个 Channel 的写出操作 */
    protected AioWriteThreadGroup writeThreadGroup;
}
