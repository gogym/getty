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
 * 事件循环
 *
 * @author gogym.ggj
 * @ClassName EventLoop.java
 * @Description
 * @createTime 2020/12/15 13:59:32
 */
public interface EventLoop {
    /**
     * 运行
     */
    void run();

    /**
     * 关闭
     */
    void shutdown();

    /**
     * 获取selector
     *
     * @return
     */
    SelectedSelector getSelector();

}
