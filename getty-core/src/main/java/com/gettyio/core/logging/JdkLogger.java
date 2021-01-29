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
 * JdkLogger 实现
 *
 * @author gogym
 * @version 1.0.0
 * @className JdkLogger.java
 * @description
 * @date 2020/12/31
 */
class JdkLogger extends AbstractInternalLogger {

    private static final long serialVersionUID = -1767272577989225979L;

    static final String SELF = JdkLogger.class.getName();
    static final String SUPER = AbstractInternalLogger.class.getName();

    final transient Logger logger;

    JdkLogger(Logger logger) {
        super(logger.getName());
        this.logger = logger;
    }


    @Override
    public boolean isTraceEnabled() {
        return logger.isLoggable(Level.FINEST);
    }


    @Override
    public void trace(String msg) {
        if (logger.isLoggable(Level.FINEST)) {
            log(SELF, Level.FINEST, msg, null);
        }
    }


    @Override
    public void trace(String format, Object arg) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(SELF, Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void trace(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(SELF, Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void trace(String format, Object... argArray) {
        if (logger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
            log(SELF, Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void trace(String msg, Throwable t) {
        if (logger.isLoggable(Level.FINEST)) {
            log(SELF, Level.FINEST, msg, t);
        }
    }


    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }


    @Override
    public void debug(String msg) {
        if (logger.isLoggable(Level.FINE)) {
            log(SELF, Level.FINE, msg, null);
        }
    }


    @Override
    public void debug(String format, Object arg) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(SELF, Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void debug(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(SELF, Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void debug(String format, Object... argArray) {
        if (logger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
            log(SELF, Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void debug(String msg, Throwable t) {
        if (logger.isLoggable(Level.FINE)) {
            log(SELF, Level.FINE, msg, t);
        }
    }


    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }


    @Override
    public void info(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            log(SELF, Level.INFO, msg, null);
        }
    }


    @Override
    public void info(String format, Object arg) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(SELF, Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void info(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(SELF, Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void info(String format, Object... argArray) {
        if (logger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
            log(SELF, Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void info(String msg, Throwable t) {
        if (logger.isLoggable(Level.INFO)) {
            log(SELF, Level.INFO, msg, t);
        }
    }


    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }


    @Override
    public void warn(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            log(SELF, Level.WARNING, msg, null);
        }
    }


    @Override
    public void warn(String format, Object arg) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(SELF, Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void warn(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(SELF, Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void warn(String format, Object... argArray) {
        if (logger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
            log(SELF, Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void warn(String msg, Throwable t) {
        if (logger.isLoggable(Level.WARNING)) {
            log(SELF, Level.WARNING, msg, t);
        }
    }


    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }


    @Override
    public void error(String msg) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(SELF, Level.SEVERE, msg, null);
        }
    }


    @Override
    public void error(String format, Object arg) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            log(SELF, Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void error(String format, Object argA, Object argB) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            log(SELF, Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void error(String format, Object... arguments) {
        if (logger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(SELF, Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }


    @Override
    public void error(String msg, Throwable t) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(SELF, Level.SEVERE, msg, t);
        }
    }


    private void log(String callerFQCN, Level level, String msg, Throwable t) {
        //millis和thread由构造函数填充
        LogRecord record = new LogRecord(level, msg);
        record.setLoggerName(name());
        record.setThrown(t);
        fillCallerData(callerFQCN, record);
        logger.log(record);
    }


    /**
     * 如有可能填调用者者信息
     *
     * @param record
     */
    private static void fillCallerData(String callerFQCN, LogRecord record) {
        StackTraceElement[] steArray = new Throwable().getStackTrace();

        int selfIndex = -1;
        for (int i = 0; i < steArray.length; i++) {
            final String className = steArray[i].getClassName();
            if (className.equals(callerFQCN) || className.equals(SUPER)) {
                selfIndex = i;
                break;
            }
        }

        int found = -1;
        for (int i = selfIndex + 1; i < steArray.length; i++) {
            final String className = steArray[i].getClassName();
            if (!(className.equals(callerFQCN) || className.equals(SUPER))) {
                found = i;
                break;
            }
        }

        if (found != -1) {
            StackTraceElement ste = steArray[found];
            record.setSourceClassName(ste.getClassName());
            record.setSourceMethodName(ste.getMethodName());
        }
    }
}
