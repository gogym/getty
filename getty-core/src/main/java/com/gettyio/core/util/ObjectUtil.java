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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;

/**
 * A grab-bag of useful utility methods.
 */
public final class ObjectUtil {

    private ObjectUtil() {
    }

    /**
     * Checks that the given argument is not null. If it is, throws {@link NullPointerException}.
     * Otherwise, returns the argument.
     *
     * @param arg  arg
     * @param text text
     * @return T t
     */
    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    /**
     * Checks that the given argument is strictly positive. If it is not, throws {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param i    i
     * @param name name
     * @return int
     */
    public static int checkPositive(int i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * Checks that the given argument is strictly positive. If it is not, throws {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param i    i
     * @param name name
     * @return long
     */
    public static long checkPositive(long i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * Checks that the given argument is positive or zero. If it is not , throws {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param i    i
     * @param name name
     * @return int
     */
    public static int checkPositiveOrZero(int i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * Checks that the given argument is positive or zero. If it is not, throws {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param i    i
     * @param name name
     * @return long
     */
    public static long checkPositiveOrZero(long i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * Checks that the given argument is neither null nor empty.
     * If it is, throws {@link NullPointerException} or {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param name  name
     * @param array array
     * @return T
     */
    public static <T> T[] checkNonEmpty(T[] array, String name) {
        checkNotNull(array, name);
        checkPositive(array.length, name + ".length");
        return array;
    }

    /**
     * Checks that the given argument is neither null nor empty.
     * If it is, throws {@link NullPointerException} or {@link IllegalArgumentException}.
     * Otherwise, returns the argument.
     *
     * @param name       name
     * @param collection collection
     * @return t
     */
    public static <T extends Collection<?>> T checkNonEmpty(T collection, String name) {
        checkNotNull(collection, name);
        checkPositive(collection.size(), name + ".size");
        return collection;
    }

    /**
     * Resolves a possibly null Integer to a primitive int, using a default value.
     *
     * @param wrapper      the wrapper
     * @param defaultValue the default value
     * @return the primitive value
     */
    public static int intValue(Integer wrapper, int defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }

    /**
     * Resolves a possibly null Long to a primitive long, using a default value.
     *
     * @param wrapper      the wrapper
     * @param defaultValue the default value
     * @return the primitive value
     */
    public static long longValue(Long wrapper, long defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }


    /**
     * <li>方法名：toInt
     * <li>@param b
     * <li>@return
     * <li>返回类型：int
     */
    public static int toInt(byte... b) {
        return (int) toLong(b);
    }

    /**
     * <li>方法名：toLong
     * <li>@param b
     * <li>@return
     * <li>返回类型：long
     */
    public static long toLong(byte... b) {
        int mask = 0xff;
        int temp = 0;
        long res = 0;
        int byteslen = b.length;
        if (byteslen > 8) {
            return Long.valueOf(0L);
        }
        for (int i = 0; i < byteslen; i++) {
            res <<= 8;
            temp = b[i] & mask;
            res |= temp;
        }
        return res;
    }


    /**
     * <li>方法名：numberToByte
     * <li>@param l
     * <li>@param length
     * <li>@return
     * <li>返回类型：byte[]
     */
    private static byte[] numberToByte(long l, int length) {
        byte[] bts = new byte[length];
        for (int i = 0; i < length; i++) {
            bts[i] = (byte) (l >> ((length - i - 1) * 8));
        }
        return bts;
    }

    /**
     * <li>方法名：shortToByte
     * <li>@param i
     * <li>@return
     * <li>返回类型：byte[]
     */
    public static byte[] shortToByte(int i) {
        return numberToByte(i, 2);
    }


    /**
     * <li>方法名：longToByte
     * <li>@param i
     * <li>@return
     * <li>返回类型：byte[]
     */
    public static byte[] longToByte(long i) {
        return numberToByte(i, 8);
    }

    /**
     * 对象转数组
     *
     * @param obj
     * @return
     */
    public static byte[] ObjToByteArray(Object obj) {
        byte[] bytes = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            bytes = bos.toByteArray();
            oos.close();
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return bytes;
    }
}
