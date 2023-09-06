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


/**
 * SystemPropertyUtil.java
 * <p>
 * 用于检索和解析Java系统属性的值。
 *
 * @author:gogym 2020/4/9
 * Copyright by gettyio.com
 */
public final class SystemPropertyUtil {

    private SystemPropertyUtil() {
    }

    /**
     * 返回带有指定的{@code key}的Java系统属性的值，如果属性访问失败则返回{@code null}。
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


}
