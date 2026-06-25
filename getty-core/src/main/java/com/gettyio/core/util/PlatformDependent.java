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

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

/**
 * 平台相关操作工具类。
 * <p>
 * 提供操作系统类型检测（Windows/Android），系统 ClassLoader 获取等功能。
 * 检测结果在首次调用时缓存，后续调用直接返回缓存值。
 * </p>
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class PlatformDependent {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent.class);

    /** 缓存 Windows 检测结果，避免每次调用都解析系统属性 */
    private static final boolean IS_WINDOWS = detectWindows();

    /** 缓存 Android 检测结果 */
    private static final boolean IS_ANDROID = detectAndroid();

    private PlatformDependent() {
    }

    /**
     * 检测当前是否为 Windows 平台
     *
     * @return {@code true} 如果 JVM 运行在 Windows 上
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * 检测当前是否为 Android 平台
     *
     * @return {@code true} 如果当前平台是 Android
     */
    public static boolean isAndroid() {
        return IS_ANDROID;
    }

    /**
     * 获取系统 {@link ClassLoader}。
     * <p>
     * 在存在 SecurityManager 时通过特权操作获取。
     * </p>
     *
     * @return 系统 ClassLoader
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

    // ===================== 内部检测方法 =====================

    /**
     * 检测 Windows 平台
     */
    private static boolean detectWindows() {
        boolean windows = SystemPropertyUtil.get("os.name", "")
                .toLowerCase(Locale.US).contains("win");
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }

    /**
     * 检测 Android 平台
     */
    private static boolean detectAndroid() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, getSystemClassLoader());
            android = true;
        } catch (Exception e) {
            android = false;
        }
        if (android) {
            logger.debug("Platform: Android");
        }
        return android;
    }
}
