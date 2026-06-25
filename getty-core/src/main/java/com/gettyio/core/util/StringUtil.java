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

import java.util.Formatter;

/**
 * 字符串工具类。
 * <p>
 * 提供平台换行符常量、简单类名获取、空白字符查找、字符串空判断等常用操作。
 * </p>
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class StringUtil {

    /** 当前平台的换行符 */
    public static final String NEWLINE;

    /** 空字符串常量 */
    public static final String EMPTY_STRING = "";

    static {
        String newLine;
        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }
        NEWLINE = newLine;
    }

    private StringUtil() {
    }

    /**
     * 获取对象的简单类名（不含包名）
     *
     * @param o 对象实例
     * @return 简单类名，null 对象返回 "null_object"
     */
    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        }
        return simpleClassName(o.getClass());
    }

    /**
     * 获取 Class 的简单类名（不含包名）
     *
     * @param clazz Class 对象
     * @return 简单类名，null 返回 "null_class"
     */
    public static String simpleClassName(Class<?> clazz) {
        if (clazz == null) {
            return "null_class";
        }
        Package pkg = clazz.getPackage();
        if (pkg != null) {
            return clazz.getName().substring(pkg.getName().length() + 1);
        }
        return clazz.getName();
    }

    /**
     * 从指定位置开始向后查找第一个非空白字符的索引
     *
     * @param sb     待查找的字符串
     * @param offset 起始偏移量
     * @return 第一个非空白字符的索引，如全部为空白则返回字符串长度
     */
    public static int findNonWhitespace(String sb, int offset) {
        int len = sb.length();
        for (int i = offset; i < len; i++) {
            if (!Character.isWhitespace(sb.charAt(i))) {
                return i;
            }
        }
        return len;
    }

    /**
     * 从指定位置开始向后查找第一个空白字符的索引
     *
     * @param sb     待查找的字符串
     * @param offset 起始偏移量
     * @return 第一个空白字符的索引，如无空白则返回字符串长度
     */
    public static int findWhitespace(String sb, int offset) {
        int len = sb.length();
        for (int i = offset; i < len; i++) {
            if (Character.isWhitespace(sb.charAt(i))) {
                return i;
            }
        }
        return len;
    }

    /**
     * 从末尾向前查找最后一个非空白字符的位置
     *
     * @param sb 待查找的字符串
     * @return 最后一个非空白字符之后的位置（即 trim 后的长度）
     */
    public static int findEndOfString(String sb) {
        for (int i = sb.length(); i > 0; i--) {
            if (!Character.isWhitespace(sb.charAt(i - 1))) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 判断字符串是否为 null 或空字符串
     *
     * @param str 待判断的字符串
     * @return {@code true} 如果 str 为 null 或长度为 0
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
