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
package com.gettyio.core.channel.loop;

/**
 * NIO 事件循环接口。
 * <p>
 * 负责在独立线程中轮询 Selector，处理 I/O 事件（连接、读取、写入）。
 * </p>
 *
 * @author gogym
 */
public interface EventLoop {

    /** 启动事件循环 */
    void run();

    /** 停止事件循环并释放资源 */
    void shutdown();

    /**
     * 获取关联的选择器。
     *
     * @return SelectedSelector
     */
    SelectedSelector getSelector();
}
