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

import com.gettyio.core.util.StringUtil;

/**
 * {@link InternalLogger} 的抽象基类。
 * <p>
 * 持有日志记录器的名称，所有子类通过继承获得统一的 {@link #name()} 和 {@link #toString()} 实现。
 * 子类只需实现各级别的 {@code isXxxEnabled()} 和日志输出方法。
 * </p>
 */
public abstract class AbstractInternalLogger implements InternalLogger {

    /** 日志记录器名称（通常是类的全限定名） */
    private final String name;

    protected AbstractInternalLogger(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + name + ')';
    }
}
