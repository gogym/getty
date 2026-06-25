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

import org.slf4j.spi.LocationAwareLogger;

import static org.slf4j.spi.LocationAwareLogger.*;

/**
 * 支持调用者位置感知的 SLF4J 日志适配器。
 * <p>
 * 当底层 SLF4J 实现提供 {@link LocationAwareLogger} 接口时使用。相比 {@link Slf4JLogger}，
 * 本类通过 {@link LocationAwareLogger#log} 方法保留精确的调用者类名、方法名和行号。
 * </p>
 *
 * <p><b>性能优化：</b></p>
 * <ul>
 *   <li>简单消息方法（无参数）：直接委托给 {@code logger.log()}，SLF4J 内部检查级别，
 *       避免冗余的 {@code isXxxEnabled()} 调用</li>
 *   <li>参数化方法（带格式参数）：先检查 {@code isXxxEnabled()}，
 *       避免禁用级别时执行 {@link MessageFormatter} 格式化的开销</li>
 * </ul>
 */
final class LocationAwareSlf4JLogger extends AbstractInternalLogger {

    private static final long serialVersionUID = -8292030083201538180L;

    /** 本类的全限定名，传递给 LocationAwareLogger 以正确识别调用者位置 */
    private static final String FQCN = LocationAwareSlf4JLogger.class.getName();

    private final transient LocationAwareLogger logger;

    LocationAwareSlf4JLogger(LocationAwareLogger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    // ---- 内部委托方法 ----

    private void log(int level, String message) {
        logger.log(null, FQCN, level, message, null, null);
    }

    private void log(int level, String message, Throwable cause) {
        logger.log(null, FQCN, level, message, null, cause);
    }

    private void log(int level, org.slf4j.helpers.FormattingTuple tuple) {
        logger.log(null, FQCN, level, tuple.getMessage(), tuple.getArgArray(), tuple.getThrowable());
    }

    // ---- TRACE ----
    // 简单消息直接委托（SLF4J 内部检查级别）；参数化消息先检查避免格式化开销

    @Override public boolean isTraceEnabled() { return logger.isTraceEnabled(); }
    @Override public void trace(String msg) { log(TRACE_INT, msg); }
    @Override public void trace(String msg, Throwable t) { log(TRACE_INT, msg, t); }

    @Override
    public void trace(String format, Object arg) {
        if (logger.isTraceEnabled()) { log(TRACE_INT, org.slf4j.helpers.MessageFormatter.format(format, arg)); }
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        if (logger.isTraceEnabled()) { log(TRACE_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB)); }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (logger.isTraceEnabled()) { log(TRACE_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments)); }
    }

    // ---- DEBUG ----

    @Override public boolean isDebugEnabled() { return logger.isDebugEnabled(); }
    @Override public void debug(String msg) { log(DEBUG_INT, msg); }
    @Override public void debug(String msg, Throwable t) { log(DEBUG_INT, msg, t); }

    @Override
    public void debug(String format, Object arg) {
        if (logger.isDebugEnabled()) { log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.format(format, arg)); }
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        if (logger.isDebugEnabled()) { log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB)); }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (logger.isDebugEnabled()) { log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments)); }
    }

    // ---- INFO ----

    @Override public boolean isInfoEnabled() { return logger.isInfoEnabled(); }
    @Override public void info(String msg) { log(INFO_INT, msg); }
    @Override public void info(String msg, Throwable t) { log(INFO_INT, msg, t); }

    @Override
    public void info(String format, Object arg) {
        if (logger.isInfoEnabled()) { log(INFO_INT, org.slf4j.helpers.MessageFormatter.format(format, arg)); }
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        if (logger.isInfoEnabled()) { log(INFO_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB)); }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (logger.isInfoEnabled()) { log(INFO_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments)); }
    }

    // ---- WARN ----

    @Override public boolean isWarnEnabled() { return logger.isWarnEnabled(); }
    @Override public void warn(String msg) { log(WARN_INT, msg); }
    @Override public void warn(String msg, Throwable t) { log(WARN_INT, msg, t); }

    @Override
    public void warn(String format, Object arg) {
        if (logger.isWarnEnabled()) { log(WARN_INT, org.slf4j.helpers.MessageFormatter.format(format, arg)); }
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        if (logger.isWarnEnabled()) { log(WARN_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB)); }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (logger.isWarnEnabled()) { log(WARN_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments)); }
    }

    // ---- ERROR ----

    @Override public boolean isErrorEnabled() { return logger.isErrorEnabled(); }
    @Override public void error(String msg) { log(ERROR_INT, msg); }
    @Override public void error(String msg, Throwable t) { log(ERROR_INT, msg, t); }

    @Override
    public void error(String format, Object arg) {
        if (logger.isErrorEnabled()) { log(ERROR_INT, org.slf4j.helpers.MessageFormatter.format(format, arg)); }
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        if (logger.isErrorEnabled()) { log(ERROR_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB)); }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (logger.isErrorEnabled()) { log(ERROR_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments)); }
    }
}
