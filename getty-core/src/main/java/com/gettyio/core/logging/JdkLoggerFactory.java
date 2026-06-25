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
 * JDK 内置日志（{@code java.util.logging}）工厂。
 * <p>
 * 当 SLF4J 不可用时作为回退方案使用。也可通过
 * {@link InternalLoggerFactory#setDefaultFactory} 手动指定。
 * </p>
 */
public class JdkLoggerFactory extends InternalLoggerFactory {

    /** 全局单例 */
    public static final InternalLoggerFactory INSTANCE = new JdkLoggerFactory();

    @Override
    public InternalLogger newInstance(String name) {
        return new JdkLogger(java.util.logging.Logger.getLogger(name));
    }
}
