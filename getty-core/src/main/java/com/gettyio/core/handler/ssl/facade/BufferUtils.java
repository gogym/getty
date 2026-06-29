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
package com.gettyio.core.handler.ssl.facade;

import java.nio.ByteBuffer;

/**
 * ByteBuffer 工具类。
 * <p>提供 SSL 缓冲区操作中常用的复制功能。</p>
 */
class BufferUtils {

    private BufferUtils() {
    }

    /**
     * 将源缓冲区的内容复制到目标缓冲区，并翻转目标缓冲区以供读取。
     *
     * @param from 源缓冲区（position 到 limit 之间的数据）
     * @param to   目标缓冲区
     */
    static void copy(ByteBuffer from, ByteBuffer to) {
        to.put(from);
        to.flip();
    }
}
