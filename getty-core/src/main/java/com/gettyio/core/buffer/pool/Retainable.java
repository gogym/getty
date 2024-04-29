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
 * Retainable 接口定义了一个对象被保留（或增加引用计数）的行为。
 * 该接口仅包含一个方法 retain()，用于增加对象的引用计数。
 */
public interface Retainable {
    /**
     * retain 方法用于保留当前对象，即增加对象的引用计数。
     * 该方法没有参数和返回值，其主要作用是影响对象的生命周期，
     * 通常用于对象池或引用计数管理的场景中。
     */
    void retain();
}
