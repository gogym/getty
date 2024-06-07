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
package com.gettyio.core.buffer.bytebuf;


/**
 * 接口ReferenceCounted定义了对象的引用计数管理方法。
 * 引用计数是一种内存管理技术，用于跟踪和管理对象的生命周期，特别是在并发环境中。
 * 对象的引用计数增加时表示对该对象的额外持有，减少时表示释放对该对象的持有。
 * 当对象的引用计数减少到0时，通常表示该对象不再被需要，可以被安全地释放。
 */
public interface ReferenceCounted {

    /**
     * 获取当前对象的引用计数。
     *
     * @return 当前对象的引用计数。
     */
    int refCnt();

    /**
     * 将对象的引用计数增加1。
     *
     * @return 增加引用计数后的对象本身，以便支持链式调用。
     */

    ReferenceCounted retain();

    /**
     * 将对象的引用计数增加指定的数量。
     *
     * @param increment 引用计数增加的数量。
     * @return 增加引用计数后的对象本身，以便支持链式调用。
     */
    ReferenceCounted retain(int increment);

    /**
     * 尝试将对象的引用计数减少1，并检查是否可以释放对象。
     *
     * @return 如果引用计数减少后不为0，则返回true；如果减少后为0，表示对象可以被释放，返回false。
     */
    boolean release();

    /**
     * 尝试将对象的引用计数减少指定的数量，并检查是否可以释放对象。
     *
     * @param decrement 引用计数减少的数量。
     * @return 如果引用计数减少后不为0，则返回true；如果减少后为0，表示对象可以被释放，返回false。
     */
    boolean release(int decrement);
}

