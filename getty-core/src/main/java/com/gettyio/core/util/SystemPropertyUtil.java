/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.util;


import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.regex.Pattern;


/**
 * SystemPropertyUtil.java
 *
 * @description:用于检索和解析Java系统属性的值。
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public final class SystemPropertyUtil {

    private static boolean initializedLogger;
    private final static InternalLogger logger = InternalLoggerFactory.getInstance(SystemPropertyUtil.class);

    private static boolean loggedException;

    static {
        initializedLogger = false;
        initializedLogger = true;
    }

    /**
     * 当且仅当具有指定的{@code key}的系统属性存在时，返回{@code true}
     */
    public static boolean contains(String key) {
        return get(key) != null;
    }

    /**
     * 返回指定的Java系统属性的值
     * {@code key}, 如果属性访问失败，则返回指定的默认值
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * 返回指定的Java系统属性的值
     * {@code key}, 如果属性访问失败，则返回指定的默认值
     */
    public static String get(final String key, String def) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty.");
        }

        String value = null;
        try {
            if (System.getSecurityManager() == null) {
                value = System.getProperty(key);
            } else {
                value = AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(key);
                    }
                });
            }
        } catch (Exception e) {
            if (!loggedException) {
                log("Unable to retrieve a system property '" + key + "'; default values will be used.", e);
                loggedException = true;
            }
        }

        if (value == null) {
            return def;
        }

        return value;
    }

    /**
     * 返回指定的Java系统属性的值
     * {@code key}, 如果属性访问失败，则返回指定的默认值
     */
    public static boolean getBoolean(String key, boolean def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (value.isEmpty()) {
            return true;
        }

        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
            return true;
        }

        if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
            return false;
        }

        log("Unable to parse the boolean system property '" + key + "':" + value + " - " + "using the default value: " + def);

        return def;
    }

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]+");

    /**
     * 返回指定的Java系统属性的值
     * {@code key}, 如果属性访问失败，则返回指定的默认值
     */
    public static int getInt(String key, int def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (INTEGER_PATTERN.matcher(value).matches()) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                // Ignore
            }
        }

        log("Unable to parse the integer system property '" + key + "':" + value + " - " + "using the default value: " + def);

        return def;
    }

    /**
     * 返回指定的Java系统属性的值
     * {@code key}, 如果属性访问失败，则返回指定的默认值
     */
    public static long getLong(String key, long def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (INTEGER_PATTERN.matcher(value).matches()) {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                // Ignore
            }
        }
        log("Unable to parse the long integer system property '" + key + "':" + value + " - " + "using the default value: " + def);
        return def;
    }

    private static void log(String msg) {
        if (initializedLogger) {
            logger.warn(msg);
        }
    }

    private static void log(String msg, Exception e) {
        if (initializedLogger) {
            logger.warn(msg, e);
        }
    }

    private SystemPropertyUtil() {
        // Unused
    }
}
