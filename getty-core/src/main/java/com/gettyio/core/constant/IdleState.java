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
package com.gettyio.core.constant;

/**
 * 连接空闲状态枚举。
 * <p>
 * 用于 {@code IdleStateHandler} 检测连接的读写空闲状态，
 * 当连接在指定时间内未进行读或写操作时触发对应的空闲事件。
 *
 * @author gogym.ggj
 * @see com.gettyio.expansion.handler.timeout.IdleStateHandler
 */
public enum IdleState {

    /**
     * 读空闲：连接在一段时间内未接收到任何数据。
     */
    READER_IDLE,

    /**
     * 写空闲：连接在一段时间内未发送任何数据。
     */
    WRITER_IDLE
}
