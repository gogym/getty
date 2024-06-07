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
 * 引用计数异常类，用于表示引用计数操作后的状态不合法。
 * 此类异常表明在进行引用计数操作（增加或减少）后，计数结果不处于预期的合法范围内。
 */
public class IllegalReferenceCountException extends IllegalStateException {

    /**
     * 构造函数，用于创建引用计数不合法的异常实例。
     *
     * @param refCnt 当前的引用计数值，该值不合法。
     */
    public IllegalReferenceCountException(int refCnt) {
        this("refCnt: " + refCnt);
    }

    /**
     * 构造函数，用于创建引用计数增加或减少操作不合法的异常实例。
     *
     * @param refCnt    当前的引用计数值，该值不合法。
     * @param increment 引用计数的操作值，可能是增加或减少的量。
     *                  如果increment大于0，表示增加操作；如果小于0，表示减少操作。
     */
    public IllegalReferenceCountException(int refCnt, int increment) {
        this("refCnt: " + refCnt + ", " + (increment > 0 ? "increment: " + increment : "decrement: " + -increment));
    }

    /**
     * 构造函数，用于创建具有自定义错误消息的引用计数异常实例。
     *
     * @param message 错误消息，描述引用计数的详细问题。
     */
    public IllegalReferenceCountException(String message) {
        super(message);
    }

}

