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

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日期时间工具类。
 * <p>
 * 使用 {@link ThreadLocal} 缓存 {@link SimpleDateFormat} 实例，
 * 避免每次调用时创建新对象，同时保证线程安全。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class DateTimeUtil {

    /** 默认日期时间格式 */
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * ThreadLocal 缓存的 SimpleDateFormat，每个线程独享一个实例。
     * 避免重复创建对象带来的 GC 压力，同时保证线程安全。
     */
    private static final ThreadLocal<SimpleDateFormat> DEFAULT_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(DEFAULT_PATTERN));

    private DateTimeUtil() {
    }

    /**
     * 获取当前系统时间（默认格式：yyyy-MM-dd HH:mm:ss）
     *
     * @return 格式化后的当前时间字符串
     */
    public static String getCurrentTime() {
        return DEFAULT_FORMAT.get().format(new Date());
    }

    /**
     * 使用指定格式获取当前系统时间
     *
     * @param pattern 日期时间格式，如 "yyyy-MM-dd"
     * @return 格式化后的当前时间字符串
     */
    public static String getCurrentTime(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date());
    }

    /**
     * 将时间戳格式化为默认格式的字符串
     *
     * @param timestamp 毫秒时间戳
     * @return 格式化后的时间字符串
     */
    public static String format(long timestamp) {
        return DEFAULT_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 将 Date 对象格式化为默认格式的字符串
     *
     * @param date 日期对象
     * @return 格式化后的时间字符串
     */
    public static String format(Date date) {
        return DEFAULT_FORMAT.get().format(date);
    }
}
