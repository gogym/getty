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
 * 日志工厂抽象基类。
 * <p>
 * 采用工厂模式创建 {@link InternalLogger} 实例。默认优先使用 SLF4J，
 * 若 SLF4J 不可用则自动回退到 JDK 内置的 {@code java.util.logging}。
 * </p>
 *
 * <p><b>线程安全：</b>{@link #getDefaultFactory()} 使用双重检查锁（DCL），
 * 确保多线程环境下只初始化一次。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * private static final InternalLogger logger = InternalLoggerFactory.getInstance(MyClass.class);
 * }</pre>
 */
public abstract class InternalLoggerFactory {

    /** 默认工厂实例（volatile 保证 DCL 可见性） */
    private static volatile InternalLoggerFactory defaultFactory;

    /**
     * 获取默认工厂实例。
     * <p>优先尝试 SLF4J，失败时回退到 JDK Logger。</p>
     */
    public static InternalLoggerFactory getDefaultFactory() {
        InternalLoggerFactory f = defaultFactory;
        if (f == null) {
            synchronized (InternalLoggerFactory.class) {
                f = defaultFactory;
                if (f == null) {
                    f = newDefaultFactory();
                    defaultFactory = f;
                }
            }
        }
        return f;
    }

    /**
     * 替换默认工厂实例。
     *
     * @param factory 新的工厂实例，不能为 null
     */
    public static void setDefaultFactory(InternalLoggerFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        defaultFactory = factory;
    }

    /**
     * 使用指定类的名称创建日志记录器。
     *
     * @param clazz 日志记录器关联的类
     * @return 日志记录器实例
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return getInstance(clazz.getName());
    }

    /**
     * 使用指定名称创建日志记录器。
     *
     * @param name 日志记录器名称
     * @return 日志记录器实例
     */
    public static InternalLogger getInstance(String name) {
        return getDefaultFactory().newInstance(name);
    }

    /**
     * 子类实现：创建指定名称的日志记录器实例。
     */
    protected abstract InternalLogger newInstance(String name);

    /**
     * 创建默认工厂：优先 SLF4J，失败时回退到 JDK Logger。
     */
    private static InternalLoggerFactory newDefaultFactory() {
        InternalLoggerFactory f;
        try {
            f = new Slf4JLoggerFactory(true);
            f.newInstance(InternalLoggerFactory.class.getName())
                    .debug("Using SLF4J as the default logging framework");
        } catch (Throwable ignore) {
            f = JdkLoggerFactory.INSTANCE;
            f.newInstance(InternalLoggerFactory.class.getName())
                    .debug("Using java.util.logging as the default logging framework");
        }
        return f;
    }
}
