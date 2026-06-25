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

import org.slf4j.Logger;

/**
 * SLF4J 日志适配器（简单委托模式）。
 * <p>
 * 当底层 SLF4J 实现不支持 {@link org.slf4j.spi.LocationAwareLogger} 时使用。
 * 所有方法直接委托给 SLF4J {@link Logger}，由 SLF4J 内部处理级别检查和消息格式化。
 * </p>
 *
 * <p><b>性能说明：</b>无额外开销，零中间层抽象。</p>
 */
final class Slf4JLogger extends AbstractInternalLogger {

    private static final long serialVersionUID = 108038972685130825L;

    private final transient Logger logger;

    Slf4JLogger(Logger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    @Override public boolean isTraceEnabled() { return logger.isTraceEnabled(); }
    @Override public void trace(String msg) { logger.trace(msg); }
    @Override public void trace(String format, Object arg) { logger.trace(format, arg); }
    @Override public void trace(String format, Object argA, Object argB) { logger.trace(format, argA, argB); }
    @Override public void trace(String format, Object... arguments) { logger.trace(format, arguments); }
    @Override public void trace(String msg, Throwable t) { logger.trace(msg, t); }

    @Override public boolean isDebugEnabled() { return logger.isDebugEnabled(); }
    @Override public void debug(String msg) { logger.debug(msg); }
    @Override public void debug(String format, Object arg) { logger.debug(format, arg); }
    @Override public void debug(String format, Object argA, Object argB) { logger.debug(format, argA, argB); }
    @Override public void debug(String format, Object... arguments) { logger.debug(format, arguments); }
    @Override public void debug(String msg, Throwable t) { logger.debug(msg, t); }

    @Override public boolean isInfoEnabled() { return logger.isInfoEnabled(); }
    @Override public void info(String msg) { logger.info(msg); }
    @Override public void info(String format, Object arg) { logger.info(format, arg); }
    @Override public void info(String format, Object argA, Object argB) { logger.info(format, argA, argB); }
    @Override public void info(String format, Object... arguments) { logger.info(format, arguments); }
    @Override public void info(String msg, Throwable t) { logger.info(msg, t); }

    @Override public boolean isWarnEnabled() { return logger.isWarnEnabled(); }
    @Override public void warn(String msg) { logger.warn(msg); }
    @Override public void warn(String format, Object arg) { logger.warn(format, arg); }
    @Override public void warn(String format, Object argA, Object argB) { logger.warn(format, argA, argB); }
    @Override public void warn(String format, Object... arguments) { logger.warn(format, arguments); }
    @Override public void warn(String msg, Throwable t) { logger.warn(msg, t); }

    @Override public boolean isErrorEnabled() { return logger.isErrorEnabled(); }
    @Override public void error(String msg) { logger.error(msg); }
    @Override public void error(String format, Object arg) { logger.error(format, arg); }
    @Override public void error(String format, Object argA, Object argB) { logger.error(format, argA, argB); }
    @Override public void error(String format, Object... arguments) { logger.error(format, arguments); }
    @Override public void error(String msg, Throwable t) { logger.error(msg, t); }
}
