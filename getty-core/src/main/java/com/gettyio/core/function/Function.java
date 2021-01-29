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
package com.gettyio.core.function;

/**
 * Function.java
 *
 * @description:函数式编程，基于一个输入值确定一个输出值，参考于google Guava
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface Function<F, T> {

    /**
     * 基于F值返回T
     *
     * @param input
     * @return
     */
    T apply(F input);

    /**
     * 比较
     *
     * @param object
     * @return
     */
    @Override
    boolean equals(Object object);
}
