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
 * SLF4J 日志工厂。
 * <p>
 * 根据底层 SLF4J 实现的能力自动选择适配器：
 * <ul>
 *   <li>支持 {@link org.slf4j.spi.LocationAwareLogger} 时使用 {@link LocationAwareSlf4JLogger}，
 *       可保留精确的调用者位置信息</li>
 *   <li>否则使用 {@link Slf4JLogger} 进行简单委托</li>
 * </ul>
 * </p>
 *
 * <p>如果 SLF4J 绑定为 NOP（无操作），构造时会抛出异常以触发回退到 JDK Logger。</p>
 */
public class Slf4JLoggerFactory extends InternalLoggerFactory {

    /** 全局单例 */
    @SuppressWarnings("deprecation")
    public static final InternalLoggerFactory INSTANCE = new Slf4JLoggerFactory(true);

    /**
     * 创建工厂实例。
     *
     * @param failIfNOP 为 true 时，若 SLF4J 绑定为 NOP 则抛出异常
     */
    Slf4JLoggerFactory(boolean failIfNOP) {
        assert failIfNOP;
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof org.slf4j.helpers.NOPLoggerFactory) {
            throw new NoClassDefFoundError("NOPLoggerFactory not supported");
        }
    }

    @Override
    public InternalLogger newInstance(String name) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(name);
        if (logger instanceof org.slf4j.spi.LocationAwareLogger) {
            return new LocationAwareSlf4JLogger((org.slf4j.spi.LocationAwareLogger) logger);
        }
        return new Slf4JLogger(logger);
    }
}
