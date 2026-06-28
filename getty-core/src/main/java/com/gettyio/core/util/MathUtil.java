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

/**
 * 数学运算工具类。
 * <p>
 * 提供位运算级别的高性能数学计算，适用于内存池、哈希表等
 * 需要快速计算 2 的幂次的场景。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class MathUtil {

    private MathUtil() {
    }

    /**
     * 求大于或等于 {@code value} 的最小的 2 的幂。
     * <p>
     * 利用 {@link Integer#numberOfLeadingZeros(int)} 实现，
     * 该方法在 HotSpot JVM 中会被内联为 CPU 指令（LZCNT/BSR），
     * 时间复杂度 O(1)，无分支。
     * </p>
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code value = 5} → {@code 8}</li>
     *   <li>{@code value = 8} → {@code 8}</li>
     *   <li>{@code value = 9} → {@code 16}</li>
     * </ul>
     * </p>
     * <p>
     * 限制：不适用于 {@link Integer#MIN_VALUE} 或大于 2^30 的值。
     * </p>
     *
     * @param value 搜索起点
     * @return 大于或等于 {@code value} 的最小的 2 的幂
     */
    public static int findNextPositivePowerOfTwo(final int value) {
        assert value > Integer.MIN_VALUE && value < 0x40000000;
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }
}
