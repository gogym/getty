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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Formatter;

/**
 * 通用对象与字符串工具类。
 * <p>
 * 提供参数校验、字节数组与数值互转、对象序列化、
 * 简单类名获取、空白字符查找等常用功能。
 * </p>
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class ObjectUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ObjectUtil.class);

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

    private ObjectUtil() {
    }

    // ===================== 参数校验 =====================

    /**
     * 检查参数不为 null
     *
     * @param arg  待检查的参数
     * @param text 为 null 时的异常信息
     * @param <T>  参数类型
     * @return 原参数
     * @throws NullPointerException 如果 arg 为 null
     */
    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    // ===================== 字节与数值互转 =====================

    /**
     * 字节数组转 long（大端序）
     *
     * @param b 字节数组（最多 8 字节）
     * @return long 值
     */
    public static long toLong(byte... b) {
        int len = b.length;
        if (len > 8) {
            return 0L;
        }
        long res = 0;
        for (int i = 0; i < len; i++) {
            res = (res << 8) | (b[i] & 0xFF);
        }
        return res;
    }

    // ===================== 对象序列化 =====================

    /**
     * 将可序列化对象转为字节数组。
     * <p>
     * 使用 Java 原生序列化（{@link ObjectOutputStream}），
     * 适用于实现了 {@link java.io.Serializable} 接口的对象。
     * </p>
     *
     * @param obj 可序列化对象
     * @return 序列化后的字节数组，异常时返回 null
     */
    public static byte[] ObjToByteArray(Object obj) {
        if (obj == null) {
            return null;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException ex) {
            logger.warn("Failed to serialize object to byte array", ex);
            return null;
        }
    }

    // ===================== 字符串操作 =====================

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
}
