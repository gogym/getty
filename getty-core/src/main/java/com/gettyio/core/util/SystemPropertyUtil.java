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
package com.gettyio.core.util;


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
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]+");

    private SystemPropertyUtil() {
    }

    /**
     * 返回带有指定的{@code key}的Java系统属性的值，如果属性访问失败则返回{@code null}。
     *
     * @return属性值或{@code null}
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * 返回带有指定的{@code键}的Java系统属性的值，如果属性访问失败则返回指定的默认值。
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

        }
        if (value == null) {
            return def;
        }
        return value;
    }

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
        return def;
    }

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
        return def;
    }

}
