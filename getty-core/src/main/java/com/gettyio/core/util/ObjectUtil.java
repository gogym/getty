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
import java.util.Collection;

/**
 * 对象工具类。
 * <p>
 * 提供参数校验（非空、正数、非空集合）、数值类型拆箱、
 * 字节数组与数值类型互转、对象序列化等常用功能。
 * </p>
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class ObjectUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ObjectUtil.class);

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

    /**
     * 检查 int 值为正数（> 0）
     *
     * @param i    待检查的值
     * @param name 参数名（用于异常信息）
     * @return 原值
     * @throws IllegalArgumentException 如果 i <= 0
     */
    public static int checkPositive(int i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * 检查 long 值为正数（> 0）
     *
     * @param i    待检查的值
     * @param name 参数名
     * @return 原值
     */
    public static long checkPositive(long i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * 检查 int 值非负（>= 0）
     *
     * @param i    待检查的值
     * @param name 参数名
     * @return 原值
     */
    public static int checkPositiveOrZero(int i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * 检查 long 值非负（>= 0）
     *
     * @param i    待检查的值
     * @param name 参数名
     * @return 原值
     */
    public static long checkPositiveOrZero(long i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * 检查数组非 null 且非空
     *
     * @param array 待检查的数组
     * @param name  参数名
     * @param <T>   数组元素类型
     * @return 原数组
     */
    public static <T> T[] checkNonEmpty(T[] array, String name) {
        checkNotNull(array, name);
        checkPositive(array.length, name + ".length");
        return array;
    }

    /**
     * 检查集合非 null 且非空
     *
     * @param collection 待检查的集合
     * @param name       参数名
     * @param <T>        集合类型
     * @return 原集合
     */
    public static <T extends Collection<?>> T checkNonEmpty(T collection, String name) {
        checkNotNull(collection, name);
        checkPositive(collection.size(), name + ".size");
        return collection;
    }

    // ===================== 数值拆箱 =====================

    /**
     * Integer 安全拆箱，null 时返回默认值
     *
     * @param wrapper      Integer 包装对象
     * @param defaultValue 默认值
     * @return int 值
     */
    public static int intValue(Integer wrapper, int defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }

    /**
     * Long 安全拆箱，null 时返回默认值
     *
     * @param wrapper      Long 包装对象
     * @param defaultValue 默认值
     * @return long 值
     */
    public static long longValue(Long wrapper, long defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }

    // ===================== 字节与数值互转 =====================

    /**
     * 字节数组转 int（大端序）
     *
     * @param b 字节数组（最多 8 字节）
     * @return int 值
     */
    public static int toInt(byte... b) {
        return (int) toLong(b);
    }

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

    /**
     * 数值转字节数组（大端序，内部方法）
     *
     * @param l      数值
     * @param length 字节数组长度
     * @return 字节数组
     */
    private static byte[] numberToBytes(long l, int length) {
        byte[] bts = new byte[length];
        for (int i = 0; i < length; i++) {
            bts[i] = (byte) (l >> ((length - i - 1) * 8));
        }
        return bts;
    }

    /**
     * short/int 值转 2 字节数组（大端序）
     *
     * @param i 数值
     * @return 2 字节数组
     */
    public static byte[] shortToByte(int i) {
        return numberToBytes(i, 2);
    }

    /**
     * long 值转 8 字节数组（大端序）
     *
     * @param i 数值
     * @return 8 字节数组
     */
    public static byte[] longToByte(long i) {
        return numberToBytes(i, 8);
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
    public static byte[] objectToByteArray(Object obj) {
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

    /**
     * 将可序列化对象转为字节数组（旧方法名，保留兼容性）。
     *
     * @param obj 可序列化对象
     * @return 序列化后的字节数组
     * @deprecated 使用 {@link #objectToByteArray(Object)} 代替
     */
    @Deprecated
    public static byte[] ObjToByteArray(Object obj) {
        return objectToByteArray(obj);
    }
}
