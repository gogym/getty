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
package com.gettyio.core.logging;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SLF4J 风格的 {@code {}} 占位符消息格式化器。
 * <p>
 * 支持 1~N 个参数替换，支持嵌套数组的深度格式化，支持 {@code \\{}} 转义。
 * 仅由 {@link JdkLogger} 使用（SLF4J 实现使用自身的格式化器）。
 * </p>
 *
 * <p><b>性能说明：</b></p>
 * <ul>
 *   <li>数值类型直接拆箱为基本类型输出，避免 String.valueOf() 的中间对象</li>
 *   <li>原始类型数组使用 {@link Arrays#toString}，利用 JDK 内部优化</li>
 *   <li>对象数组使用 {@link HashSet} 检测循环引用</li>
 * </ul>
 */
final class MessageFormatter {

    private static final String DELIM = "{}";
    private static final char ESCAPE = '\\';

    private MessageFormatter() {
    }

    /**
     * 单参数替换。
     */
    static FormattingTuple format(String pattern, Object arg) {
        return arrayFormat(pattern, new Object[]{arg});
    }

    /**
     * 双参数替换。
     */
    static FormattingTuple format(String pattern, Object argA, Object argB) {
        return arrayFormat(pattern, new Object[]{argA, argB});
    }

    /**
     * 多参数替换。
     * <p>
     * 如果参数数组的最后一个元素是 {@link Throwable}，则将其提取为关联异常而非替换参数。
     * </p>
     *
     * @param pattern  包含 {@code {}} 占位符的消息模板
     * @param argArray 参数数组（可为 null）
     * @return 格式化结果
     */
    static FormattingTuple arrayFormat(String pattern, Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            return new FormattingTuple(pattern, null);
        }

        int lastIdx = argArray.length - 1;
        Object lastEntry = argArray[lastIdx];
        Throwable throwable = lastEntry instanceof Throwable ? (Throwable) lastEntry : null;

        if (pattern == null) {
            return new FormattingTuple(null, throwable);
        }

        int j = pattern.indexOf(DELIM);
        if (j == -1) {
            return new FormattingTuple(pattern, throwable);
        }

        StringBuilder sb = new StringBuilder(pattern.length() + 50);
        int i = 0;
        int argIdx = 0;

        do {
            boolean notEscaped = j == 0 || pattern.charAt(j - 1) != ESCAPE;

            if (notEscaped) {
                sb.append(pattern, i, j);
            } else {
                sb.append(pattern, i, j - 1);
                // 检查转义字符本身是否被转义: "abc x:\\{}"
                notEscaped = j >= 2 && pattern.charAt(j - 2) == ESCAPE;
            }

            i = j + 2;

            if (notEscaped) {
                appendParameter(sb, argArray[argIdx], null);
                argIdx++;
                if (argIdx > lastIdx) {
                    break;
                }
            } else {
                sb.append(DELIM);
            }

            j = pattern.indexOf(DELIM, i);
        } while (j != -1);

        sb.append(pattern, i, pattern.length());
        return new FormattingTuple(sb.toString(), argIdx <= lastIdx ? throwable : null);
    }

    // ---- 参数追加 ----

    /**
     * 深度追加参数到 StringBuilder。
     * <p>
     * 针对不同类型优化输出：
     * <ul>
     *   <li>数值类型：直接拆箱避免 String.valueOf() 开销</li>
     *   <li>原始数组：使用 {@link Arrays#toString} 的 JDK 优化实现</li>
     *   <li>对象数组：检测循环引用</li>
     *   <li>其他对象：调用 toString()，异常时安全降级</li>
     * </ul>
     * </p>
     */
    private static void appendParameter(StringBuilder sb, Object obj, Set<Object[]> seenSet) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        Class<?> clazz = obj.getClass();

        if (!clazz.isArray()) {
            appendNonArray(sb, obj, clazz);
        } else {
            appendArray(sb, obj, clazz, seenSet);
        }
    }

    /** 追加非数组对象，对数值类型做拆箱优化 */
    private static void appendNonArray(StringBuilder sb, Object obj, Class<?> clazz) {
        if (clazz == Long.class) {
            sb.append(((Long) obj).longValue());
        } else if (clazz == Integer.class || clazz == Short.class || clazz == Byte.class) {
            sb.append(((Number) obj).intValue());
        } else if (clazz == Double.class) {
            sb.append(((Double) obj).doubleValue());
        } else if (clazz == Float.class) {
            sb.append(((Float) obj).floatValue());
        } else {
            safeAppend(sb, obj);
        }
    }

    /** 追加数组对象 */
    private static void appendArray(StringBuilder sb, Object obj, Class<?> clazz, Set<Object[]> seenSet) {
        if (clazz == boolean[].class) { sb.append(Arrays.toString((boolean[]) obj)); }
        else if (clazz == byte[].class) { sb.append(Arrays.toString((byte[]) obj)); }
        else if (clazz == char[].class) { sb.append(Arrays.toString((char[]) obj)); }
        else if (clazz == short[].class) { sb.append(Arrays.toString((short[]) obj)); }
        else if (clazz == int[].class) { sb.append(Arrays.toString((int[]) obj)); }
        else if (clazz == long[].class) { sb.append(Arrays.toString((long[]) obj)); }
        else if (clazz == float[].class) { sb.append(Arrays.toString((float[]) obj)); }
        else if (clazz == double[].class) { sb.append(Arrays.toString((double[]) obj)); }
        else { appendObjectArray(sb, (Object[]) obj, seenSet); }
    }

    /** 安全调用 toString()，异常时降级输出 */
    private static void safeAppend(StringBuilder sb, Object obj) {
        try {
            sb.append(obj.toString());
        } catch (Throwable t) {
            sb.append("[FAILED toString()]");
        }
    }

    /** 追加对象数组，使用 HashSet 检测循环引用 */
    private static void appendObjectArray(StringBuilder sb, Object[] array, Set<Object[]> seenSet) {
        if (array.length == 0) {
            sb.append("[]");
            return;
        }
        if (seenSet == null) {
            seenSet = new HashSet<>(array.length);
        }
        if (seenSet.add(array)) {
            sb.append('[');
            appendParameter(sb, array[0], seenSet);
            for (int i = 1; i < array.length; i++) {
                sb.append(", ");
                appendParameter(sb, array[i], seenSet);
            }
            sb.append(']');
            seenSet.remove(array);
        } else {
            sb.append("[...]");
        }
    }
}
