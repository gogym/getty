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
package com.gettyio.core.buffer.pool;

/**
 * ByteBufferPool接口定义了ByteBuffer对象池的 behavior。
 */
public interface ByteBufferPool {


    /**
     * 申请一个缓冲区
     *
     * @param size
     * @param direct
     * @return
     */
    RetainableByteBuffer acquire(int size, boolean direct);

    /**
     * 申请一个缓冲区
     *
     * @param size
     * @return
     */
    RetainableByteBuffer acquire(int size);


}
