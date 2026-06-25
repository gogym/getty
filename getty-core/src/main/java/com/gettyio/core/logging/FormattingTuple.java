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
package com.gettyio.core.logging;

/**
 * 消息格式化结果容器。
 * <p>
 * 保存 {@link MessageFormatter} 格式化后的消息字符串和可选的关联异常。
 * 不可变设计，线程安全。
 * </p>
 */
final class FormattingTuple {

    private final String message;
    private final Throwable throwable;

    FormattingTuple(String message, Throwable throwable) {
        this.message = message;
        this.throwable = throwable;
    }

    /** 格式化后的消息字符串 */
    String getMessage() {
        return message;
    }

    /** 关联的异常（可能为 null） */
    Throwable getThrowable() {
        return throwable;
    }
}
