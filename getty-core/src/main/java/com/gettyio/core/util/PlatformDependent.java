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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

/**
 * 包外操作堆外内存，调用这个类
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class PlatformDependent {

    private final static Logger logger = LoggerFactory.getLogger(PlatformDependent.class);

    /**
     * 只支持静态方法调用
     */
    private PlatformDependent() {

    }

    /**
     * Return {@code true} 如果JVM在Windows上运行
     */
    public static boolean isWindows() {
        boolean windows = SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("win");
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }

    /**
     * 当且仅当当前平台是Android时返回{@code true}
     */
    public static boolean isAndroid() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, getSystemClassLoader());
            android = true;
        } catch (Exception e) {
            // 无法加载Android中唯一可用的类。
            android = false;
        }

        if (android) {
            logger.debug("Platform: Android");
        }
        return android;
    }


    /**
     * 返回系统 {@link ClassLoader}.
     */
    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return ClassLoader.getSystemClassLoader();
                }
            });
        }
    }


}
