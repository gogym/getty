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
package com.gettyio.core.channel;


/**
 * ChannelState.java
 *
 * @description:通道状态
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public enum ChannelState {
    /**
     * 新的连接
     */
    NEW_CHANNEL,
    /**
     * 连接关闭。
     */
    CHANNEL_CLOSED,
    /**
     * 读通道已关闭。
     */
    INPUT_SHUTDOWN,
    /**
     * 写通道已关闭。
     */
    OUTPUT_SHUTDOWN,
    /**
     * 读操作异常。
     */
    INPUT_EXCEPTION,
    /**
     * 写操作异常。
     */
    OUTPUT_EXCEPTION,
    /**
     * ENCODE异常。
     */
    ENCODE_EXCEPTION,
    /**
     * DECODE异常。
     */
    DECODE_EXCEPTION,
    /**
     * 读取数据
     */
    CHANNEL_READ,
    /**
     * 写数据
     */
    CHANNEL_WRITE

}
