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
 * 通过这个类来打印日志
 *
 * @author gogym
 * @version 1.0.0
 * @className InternalLogger.java
 * @description
 * @date 2020/12/30
 */
public interface InternalLogger {


    /**
     * 返回这个实例的名称。
     *
     * @return 此记录器实例的名称
     */
    String name();

    /**
     * 是否为Trace级别启用了logger实例
     *
     * @return True如果该日志记录器为跟踪级别启用，
     */
    boolean isTraceEnabled();

    /**
     * 以TRACE级别记录消息
     *
     * @param msg 要记录的消息字符串
     */
    void trace(String msg);

    /**
     * 根据指定的格式和参数在TRACE级别记录消息
     *
     * @param format 格式字符串
     * @param arg    参数
     */
    void trace(String format, Object arg);

    /**
     * 根据指定的格式和参数在TRACE级别记录消息
     *
     * @param format 格式字符串
     * @param argA   参数A
     * @param argB   参数B
     */
    void trace(String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在TRACE级别记录消息
     *
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void trace(String format, Object... arguments);

    /**
     * 在TRACE级别记录一个异常(throwable)并附带一条消息
     *
     * @param msg 伴随异常的消息
     * @param t   要记录的异常(throwable)
     */
    void trace(String msg, Throwable t);

    /**
     * 在TRACE级别记录一个异常(throwable)
     *
     * @param t 要记录的异常(throwable)
     */
    void trace(Throwable t);

    /**
     * 是否为DEBUG级别启用了logger实例
     *
     * @return 如果启用来DEBUG级别，则为True
     */
    boolean isDebugEnabled();

    /**
     * 在DEBUG级别记录一条消息
     *
     * @param msg 要记录的消息字符串
     */
    void debug(String msg);

    /**
     * 根据指定的格式和参数在DEBUG级别记录消息
     *
     * @param format 格式字符串
     * @param arg    参数
     */
    void debug(String format, Object arg);

    /**
     * 根据指定的格式和参数在DEBUG级别记录消息
     *
     * @param format 格式字符串
     * @param argA   参数
     * @param argB   参数
     */
    void debug(String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在DEBUG级别记录消息
     *
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void debug(String format, Object... arguments);

    /**
     * 在DEBUG级别记录一个异常(throwable)并附带一条消息
     *
     * @param msg 伴随异常的消息
     * @param t   要记录的异常(throwable)
     */
    void debug(String msg, Throwable t);

    /**
     * 在DEBUG级别记录一个异常(throwable)
     *
     * @param t 要记录的异常(throwable)
     */
    void debug(Throwable t);

    /**
     * 是否为INFO级别启用了logger实例
     *
     * @return 如果启用来INFO级别，则为True
     */
    boolean isInfoEnabled();

    /**
     * 在INFO级别记录一条消息
     *
     * @param msg 要记录的消息字符串
     */
    void info(String msg);

    /**
     * 根据指定的格式和参数在INFO级别记录消息
     *
     * @param format 格式字符串
     * @param arg    参数
     */
    void info(String format, Object arg);

    /**
     * 根据指定的格式和参数在INFO级别记录消息
     *
     * @param format 格式字符串
     * @param argA   参数
     * @param argB   参数
     */
    void info(String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在INFO级别记录消息
     *
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void info(String format, Object... arguments);

    /**
     * 在INFO级别记录一个异常(throwable)并附带一条消息
     *
     * @param msg 伴随异常的消息
     * @param t   要记录的异常(throwable)
     */
    void info(String msg, Throwable t);

    /**
     * 在INFO级别记录一个异常(throwable)
     *
     * @param t 要记录的异常(throwable)
     */
    void info(Throwable t);

    /**
     * 是否为WARN级别启用了logger实例
     *
     * @return 如果启用来WARN级别，则为True
     */
    boolean isWarnEnabled();

    /**
     * 在WARN级别记录一条消息
     *
     * @param msg 要记录的消息字符串
     */
    void warn(String msg);

    /**
     * 根据指定的格式和参数在WARN级别记录消息
     *
     * @param format 格式字符串
     * @param arg    参数
     */
    void warn(String format, Object arg);

    /**
     * 根据指定的格式和参数在WARN级别记录消息
     *
     * @param format 格式字符串
     * @param argA   参数
     * @param argB   参数
     */
    void warn(String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在WARN级别记录消息
     *
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void warn(String format, Object... arguments);

    /**
     * 在INFO级别记录一个异常(throwable)并附带一条消息
     *
     * @param msg 伴随异常的消息
     * @param t   要记录的异常(throwable)
     */
    void warn(String msg, Throwable t);

    /**
     * 在INFO级别记录一个异常(throwable)
     *
     * @param t 要记录的异常(throwable)
     */
    void warn(Throwable t);

    /**
     * 是否为ERROR级别启用了logger实例
     *
     * @return 如果启用来ERROR级别，则为True
     */
    boolean isErrorEnabled();

    /**
     * 在ERROR级别记录一条消息
     *
     * @param msg 要记录的消息字符串
     */
    void error(String msg);

    /**
     * 根据指定的格式和参数在ERROR级别记录消息
     *
     * @param format 格式字符串
     * @param arg    参数
     */
    void error(String format, Object arg);

    /**
     * 根据指定的格式和参数在ERROR级别记录消息
     *
     * @param format 格式字符串
     * @param argA   参数
     * @param argB   参数
     */
    void error(String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在ERROR级别记录消息
     *
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void error(String format, Object... arguments);

    /**
     * 在INFO级别记录一个异常(throwable)并附带一条消息
     *
     * @param msg 伴随异常的消息
     * @param t   要记录的异常(throwable)
     */
    void error(String msg, Throwable t);

    /**
     * 在INFO级别记录一个异常(throwable)
     *
     * @param t 要记录的异常(throwable)
     */
    void error(Throwable t);

    /**
     * 是否为指定的logger实例启用
     *
     * @param level 级别
     * @return 如果启用指定级别，则为True
     */
    boolean isEnabled(InternalLogLevel level);

    /**
     * 在指定的级别记录消息
     *
     * @param level 级别
     * @param msg   要记录的消息字符串
     */
    void log(InternalLogLevel level, String msg);

    /**
     * 根据指定的格式和参数在指定级别记录消息
     *
     * @param level  级别
     * @param format 格式字符串
     * @param arg    参数
     */
    void log(InternalLogLevel level, String format, Object arg);

    /**
     * 根据指定的格式和参数在指定级别记录消息
     *
     * @param level  级别
     * @param format 格式字符串
     * @param argA   参数
     * @param argB   参数
     */
    void log(InternalLogLevel level, String format, Object argA, Object argB);

    /**
     * 根据指定的格式和参数在指定级别记录消息
     *
     * @param level     级别
     * @param format    格式字符串
     * @param arguments 包含3个或更多参数的列表
     */
    void log(InternalLogLevel level, String format, Object... arguments);

    /**
     * 在指定级别记录一个异常(throwable)并附带一条消息
     *
     * @param level 级别
     * @param msg   伴随异常的消息
     * @param t     要记录的异常(throwable)
     */
    void log(InternalLogLevel level, String msg, Throwable t);

    /**
     * 在指定级别记录一个异常(throwable)
     *
     * @param level 级别
     * @param t     要记录的异常(throwable)
     */
    void log(InternalLogLevel level, Throwable t);
}
