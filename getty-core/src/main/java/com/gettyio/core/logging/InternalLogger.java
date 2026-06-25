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
 * 内部日志记录器接口。
 * <p>
 * 定义五个日志级别（TRACE → DEBUG → INFO → WARN → ERROR）的标准化 API，
 * 支持简单消息、SLF4J 风格的 {@code {}} 参数化格式和异常记录。
 * </p>
 *
 * <p>具体实现由 {@link InternalLoggerFactory} 根据运行环境选择：
 * <ul>
 *   <li>优先使用 SLF4J（{@link Slf4JLogger} 或 {@link LocationAwareSlf4JLogger}）</li>
 *   <li>SLF4J 不可用时回退到 JDK Logger（{@link JdkLogger}）</li>
 * </ul>
 * </p>
 *
 * <p><b>性能说明：</b>参数化方法（如 {@code info(format, arg)}）仅在对应级别启用时才执行格式化，
 * 避免了禁用级别时的字符串拼接开销。</p>
 */
public interface InternalLogger {

    /**
     * 返回此日志记录器的名称。
     */
    String name();

    // ---- TRACE ----

    boolean isTraceEnabled();

    void trace(String msg);

    void trace(String format, Object arg);

    void trace(String format, Object argA, Object argB);

    void trace(String format, Object... arguments);

    void trace(String msg, Throwable t);

    // ---- DEBUG ----

    boolean isDebugEnabled();

    void debug(String msg);

    void debug(String format, Object arg);

    void debug(String format, Object argA, Object argB);

    void debug(String format, Object... arguments);

    void debug(String msg, Throwable t);

    // ---- INFO ----

    boolean isInfoEnabled();

    void info(String msg);

    void info(String format, Object arg);

    void info(String format, Object argA, Object argB);

    void info(String format, Object... arguments);

    void info(String msg, Throwable t);

    // ---- WARN ----

    boolean isWarnEnabled();

    void warn(String msg);

    void warn(String format, Object arg);

    void warn(String format, Object argA, Object argB);

    void warn(String format, Object... arguments);

    void warn(String msg, Throwable t);

    // ---- ERROR ----

    boolean isErrorEnabled();

    void error(String msg);

    void error(String format, Object arg);

    void error(String format, Object argA, Object argB);

    void error(String format, Object... arguments);

    void error(String msg, Throwable t);
}
