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
 * Java 系统属性检索工具类。
 * <p>
 * 安全地获取 Java 系统属性值，兼容 SecurityManager 环境。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class SystemPropertyUtil {

    private SystemPropertyUtil() {
    }

    /**
     * 获取指定 key 的系统属性值
     *
     * @param key 属性 key
     * @return 属性值，不存在时返回 {@code null}
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * 获取指定 key 的系统属性值，失败时返回默认值
     *
     * @param key key（不能为 null 或空字符串）
     * @param def 默认值
     * @return 属性值或默认值
     * @throws NullPointerException     如果 key 为 null
     * @throws IllegalArgumentException 如果 key 为空字符串
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
        } catch (SecurityException ignored) {
            // 权限不足时返回默认值
        }

        return value != null ? value : def;
    }
}
