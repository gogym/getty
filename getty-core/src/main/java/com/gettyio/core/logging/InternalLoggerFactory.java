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
 * 创建一个或更改默认工厂实现。允许选择什么日志记录框架,默认的工厂是SLF4J,如果SLF4J
 * 不可用,则使用JdkLoggerFactory
 *
 * @author gogym
 * @version 1.0.0
 * @className InternalLoggerFactory.java
 * @description
 * @date 2020/12/31
 */
public abstract class InternalLoggerFactory {

    /**
     * 默认工厂
     */
    private static volatile InternalLoggerFactory defaultFactory;

    @SuppressWarnings("UnusedCatchParameter")
    private static InternalLoggerFactory newDefaultFactory(String name) {
        InternalLoggerFactory f;
        try {
            f = new Slf4JLoggerFactory(true);
            f.newInstance(name).debug("Using SLF4J as the default logging framework");
        } catch (Throwable ignore1) {
            f = JdkLoggerFactory.INSTANCE;
            f.newInstance(name).debug("Using java.util.logging as the default logging framework");

        }
        return f;
    }

    /**
     * 返回默认工厂
     *
     * @return InternalLoggerFactory
     */
    public static InternalLoggerFactory getDefaultFactory() {
        if (defaultFactory == null) {
            defaultFactory = newDefaultFactory(InternalLoggerFactory.class.getName());
        }
        return defaultFactory;
    }

    /**
     * 设置默认工厂
     *
     * @param defaultFactory
     */
    public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
        if (defaultFactory == null) {
            throw new NullPointerException("defaultFactory");
        }
        InternalLoggerFactory.defaultFactory = defaultFactory;
    }

    /**
     * 使用指定类的名称创建新的记录器实例
     *
     * @param clazz c
     * @return InternalLogger
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return getInstance(clazz.getName());
    }

    /**
     * 使用指定的名称创建新的记录器实例
     *
     * @param name n
     * @return InternalLogger
     */
    public static InternalLogger getInstance(String name) {
        return getDefaultFactory().newInstance(name);
    }

    /**
     * 使用指定的名称创建新的记录器实例
     *
     * @param name n
     * @return InternalLogger
     */
    protected abstract InternalLogger newInstance(String name);

}
