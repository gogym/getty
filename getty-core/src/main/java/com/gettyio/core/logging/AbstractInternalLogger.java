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

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * @author gogym
 * @version 1.0.0
 * @className AbstractInternalLogger.java
 * @description
 * @date 2020/12/30
 */
public abstract class AbstractInternalLogger implements InternalLogger, Serializable {

    private static final long serialVersionUID = -6382972526573193470L;
    /**
     * 包分割字符
     */
    private static final char PACKAGE_SEPARATOR_CHAR = '.';
    /**
     * 异常前缀
     */
    static final String EXCEPTION_MESSAGE = "Unexpected exception:";

    /**
     * 日志输出类名称
     */
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
    public boolean isEnabled(InternalLogLevel level) {
        switch (level) {
            case TRACE:
                return isTraceEnabled();
            case DEBUG:
                return isDebugEnabled();
            case INFO:
                return isInfoEnabled();
            case WARN:
                return isWarnEnabled();
            case ERROR:
                return isErrorEnabled();
            default:
                throw new Error();
        }
    }

    @Override
    public void trace(Throwable t) {
        trace(EXCEPTION_MESSAGE, t);
    }

    @Override
    public void debug(Throwable t) {
        debug(EXCEPTION_MESSAGE, t);
    }

    @Override
    public void info(Throwable t) {
        info(EXCEPTION_MESSAGE, t);
    }

    @Override
    public void warn(Throwable t) {
        warn(EXCEPTION_MESSAGE, t);
    }

    @Override
    public void error(Throwable t) {
        error(EXCEPTION_MESSAGE, t);
    }

    @Override
    public void log(InternalLogLevel level, String msg, Throwable cause) {
        switch (level) {
            case TRACE:
                trace(msg, cause);
                break;
            case DEBUG:
                debug(msg, cause);
                break;
            case INFO:
                info(msg, cause);
                break;
            case WARN:
                warn(msg, cause);
                break;
            case ERROR:
                error(msg, cause);
                break;
            default:
                throw new Error();
        }
    }

    @Override
    public void log(InternalLogLevel level, Throwable cause) {
        switch (level) {
            case TRACE:
                trace(cause);
                break;
            case DEBUG:
                debug(cause);
                break;
            case INFO:
                info(cause);
                break;
            case WARN:
                warn(cause);
                break;
            case ERROR:
                error(cause);
                break;
            default:
                throw new Error();
        }
    }

    @Override
    public void log(InternalLogLevel level, String msg) {
        switch (level) {
            case TRACE:
                trace(msg);
                break;
            case DEBUG:
                debug(msg);
                break;
            case INFO:
                info(msg);
                break;
            case WARN:
                warn(msg);
                break;
            case ERROR:
                error(msg);
                break;
            default:
                throw new Error();
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object arg) {
        switch (level) {
            case TRACE:
                trace(format, arg);
                break;
            case DEBUG:
                debug(format, arg);
                break;
            case INFO:
                info(format, arg);
                break;
            case WARN:
                warn(format, arg);
                break;
            case ERROR:
                error(format, arg);
                break;
            default:
                throw new Error();
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object argA, Object argB) {
        switch (level) {
            case TRACE:
                trace(format, argA, argB);
                break;
            case DEBUG:
                debug(format, argA, argB);
                break;
            case INFO:
                info(format, argA, argB);
                break;
            case WARN:
                warn(format, argA, argB);
                break;
            case ERROR:
                error(format, argA, argB);
                break;
            default:
                throw new Error();
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object... arguments) {
        switch (level) {
            case TRACE:
                trace(format, arguments);
                break;
            case DEBUG:
                debug(format, arguments);
                break;
            case INFO:
                info(format, arguments);
                break;
            case WARN:
                warn(format, arguments);
                break;
            case ERROR:
                error(format, arguments);
                break;
            default:
                throw new Error();
        }
    }

    protected Object readResolve() throws ObjectStreamException {
        return InternalLoggerFactory.getInstance(name());
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + name() + ')';
    }


}
