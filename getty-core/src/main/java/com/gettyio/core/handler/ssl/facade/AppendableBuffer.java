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
 * 可追加的字节缓冲区。
 * <p>
 * 用于 SSL unwrap 操作的缓存机制：当一次 unwrap 未能完全消费密文数据时，
 * 将剩余数据缓存起来，与下一次到达的数据拼接后继续处理。
 * </p>
 *
 * <p><b>注意：</b>每次 {@link #append} 都会分配新的 ByteBuffer，
 * 适用于 SSL 场景中数据量较小的情况。</p>
 */
class AppendableBuffer {

    private ByteBuffer buffer;

    /**
     * 将新数据追加到缓存之后，返回合并后的缓冲区。
     *
     * @param data 新到达的数据
     * @return 合并后的 ByteBuffer（position=0, limit=总长度）
     */
    ByteBuffer append(ByteBuffer data) {
        ByteBuffer merged = ByteBuffer.allocate(calculateSize(data));
        if (buffer != null) {
            merged.put(buffer);
            buffer = null;
        }
        merged.put(data);
        return merged;
    }

    /**
     * 设置缓存数据（覆盖之前的缓存）。
     *
     * @param data 需要缓存的数据
     */
    void set(ByteBuffer data) {
        if (data.hasRemaining()) {
            buffer = ByteBuffer.allocate(data.remaining());
            buffer.put(data);
            buffer.rewind();
        }
    }

    /**
     * 清空缓存。
     */
    void clear() {
        buffer = null;
    }

    /**
     * 缓存中是否有剩余数据。
     */
    boolean hasRemaining() {
        return buffer != null && buffer.hasRemaining();
    }

    /**
     * 获取缓存的缓冲区（可能为 null）。
     */
    ByteBuffer get() {
        return buffer;
    }

    private int calculateSize(ByteBuffer data) {
        int size = data.limit();
        if (buffer != null) {
            size += buffer.capacity();
        }
        return size;
    }
}
