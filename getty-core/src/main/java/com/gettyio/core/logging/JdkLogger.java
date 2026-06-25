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

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 基于 JDK {@code java.util.logging} 的日志实现。
 * <p>
 * 级别映射：TRACE→FINEST, DEBUG→FINE, INFO→INFO, WARN→WARNING, ERROR→SEVERE。
 * 参数化消息使用内部 {@link MessageFormatter} 进行 {@code {}} 占位符替换。
 * </p>
 *
 * <p><b>性能说明：</b>每次日志输出前均检查 {@code isLoggable()}，
 * 避免禁用级别时执行格式化和栈帧分析。</p>
 */
class JdkLogger extends AbstractInternalLogger {

    private static final long serialVersionUID = -1767272577989225979L;

    /** 本类的全限定名，用于栈帧分析时过滤自身调用 */
    private static final String SELF = JdkLogger.class.getName();

    /** 父类的全限定名，用于栈帧分析时过滤 */
    private static final String SUPER = AbstractInternalLogger.class.getName();

    final transient Logger logger;

    JdkLogger(Logger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    // ---- TRACE (FINEST) ----

    @Override
    public boolean isTraceEnabled() {
        return logger.isLoggable(Level.FINEST);
    }

    @Override
    public void trace(String msg) {
        if (logger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, null);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (logger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, t);
        }
    }

    // ---- DEBUG (FINE) ----

    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    @Override
    public void debug(String msg) {
        if (logger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, null);
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (logger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, t);
        }
    }

    // ---- INFO ----

    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    @Override
    public void info(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, null);
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(String msg, Throwable t) {
        if (logger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, t);
        }
    }

    // ---- WARN (WARNING) ----

    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    @Override
    public void warn(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, null);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, t);
        }
    }

    // ---- ERROR (SEVERE) ----

    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    @Override
    public void error(String msg) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, null);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, t);
        }
    }

    // ---- 内部方法 ----

    /**
     * 创建 {@link LogRecord} 并填充调用者栈帧信息后提交给 JDK Logger。
     *
     * @param level 日志级别
     * @param msg   日志消息
     * @param t     关联的异常（可为 null）
     */
    private void log(Level level, String msg, Throwable t) {
        LogRecord record = new LogRecord(level, msg);
        record.setLoggerName(name());
        record.setThrown(t);
        fillCallerData(record);
        logger.log(record);
    }

    /**
     * 分析调用栈，填充真实的调用者类名和方法名。
     * <p>跳过本类（{@link JdkLogger}）和父类（{@link AbstractInternalLogger}）的栈帧，
     * 定位到实际业务代码的调用位置。</p>
     */
    private static void fillCallerData(LogRecord record) {
        StackTraceElement[] stack = new Throwable().getStackTrace();

        // 查找日志框架自身的栈帧位置
        int selfIndex = -1;
        for (int i = 0; i < stack.length; i++) {
            String className = stack[i].getClassName();
            if (className.equals(SELF) || className.equals(SUPER)) {
                selfIndex = i;
            }
        }

        // 取日志框架之后的第一个外部栈帧
        int callerIndex = selfIndex + 1;
        if (callerIndex < stack.length) {
            StackTraceElement caller = stack[callerIndex];
            record.setSourceClassName(caller.getClassName());
            record.setSourceMethodName(caller.getMethodName());
        }
    }
}
