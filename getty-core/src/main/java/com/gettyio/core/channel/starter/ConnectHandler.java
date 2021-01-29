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

import com.gettyio.core.channel.SocketChannel;

/**
 * ConnectHandler.java
 *
 * @description: 启动连接回调
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public interface ConnectHandler {

    /**
     * 连接成功回调
     *
     * @param channel
     */
    void onCompleted(SocketChannel channel);

    /**
     * 连接失败回调
     *
     * @param exc
     */
    void onFailed(Throwable exc);

}
