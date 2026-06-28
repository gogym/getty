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

import java.nio.charset.StandardCharsets;

/**
 * 自研 Base64 编解码工具类。
 * <p>
 * 实现标准 Base64 编码（RFC 4648），支持：
 * <ul>
 *   <li>byte[] / String 编解码</li>
 *   <li>指定偏移量和长度的编码</li>
 *   <li>URL 安全编码（{@code -} 和 {@code _} 替代 {@code +} 和 {@code /}）</li>
 * </ul>
 * 性能优化：查表法、位运算、零临时对象分配（解码时精确预分配输出数组）。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class Base64 {

    /** 标准编码表：A-Z, a-z, 0-9, +, / */
    private static final byte[] ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    /** URL 安全编码表：最后两位为 - 和 _ */
    private static final byte[] URL_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
    };

    /**
     * 解码表：ASCII 字符 → 6 位值。
     * 无效字符为 -1，空白字符为 -2，'=' 为 0（特殊处理）。
     * 覆盖 0~127 的 ASCII 范围。
     */
    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        // 默认填充 -1（无效）
        java.util.Arrays.fill(DECODE_TABLE, (byte) -1);
        // 标准编码映射
        for (int i = 0; i < 64; i++) {
            DECODE_TABLE[ENCODE_TABLE[i]] = (byte) i;
        }
        // 空白字符标记为 -2
        DECODE_TABLE[' '] = -2;
        DECODE_TABLE['\t'] = -2;
        DECODE_TABLE['\n'] = -2;
        DECODE_TABLE['\r'] = -2;
    }

    /** URL 安全解码表 */
    private static final byte[] URL_DECODE_TABLE = new byte[128];

    static {
        java.util.Arrays.fill(URL_DECODE_TABLE, (byte) -1);
        for (int i = 0; i < 64; i++) {
            URL_DECODE_TABLE[URL_ENCODE_TABLE[i]] = (byte) i;
        }
        URL_DECODE_TABLE[' '] = -2;
        URL_DECODE_TABLE['\t'] = -2;
        URL_DECODE_TABLE['\n'] = -2;
        URL_DECODE_TABLE['\r'] = -2;
    }

    /** 填充字符 */
    private static final byte PAD = '=';

    /** 禁止实例化 */
    private Base64() {
    }

    // ===================== 编码 =====================

    /**
     * 将 byte 数组编码为 Base64 字符串（标准编码，无换行）。
     *
     * @param source 原始数据
     * @return Base64 编码字符串
     */
    public static String encodeBytes(byte[] source) {
        return encodeBytes(source, 0, source.length, false);
    }

    /**
     * 将 byte 数组编码为 Base64 字符串。
     *
     * @param source  原始数据
     * @param options 编码选项（当前仅用于兼容，可传 0）
     * @return Base64 编码字符串
     */
    public static String encodeBytes(byte[] source, int options) {
        boolean urlSafe = (options & 16) != 0;
        return encodeBytes(source, 0, source.length, urlSafe);
    }

    /**
     * 将 byte 数组指定范围编码为 Base64 字符串。
     *
     * @param source 原始数据
     * @param off    起始偏移
     * @param len    数据长度
     * @return Base64 编码字符串
     */
    public static String encodeBytes(byte[] source, int off, int len) {
        return encodeBytes(source, off, len, false);
    }

    /**
     * 将 byte 数组指定范围编码为 Base64 字符串。
     *
     * @param source  原始数据
     * @param off     起始偏移
     * @param len     数据长度
     * @param options 编码选项
     * @return Base64 编码字符串
     */
    public static String encodeBytes(byte[] source, int off, int len, int options) {
        boolean urlSafe = (options & 16) != 0;
        return encodeBytes(source, off, len, urlSafe);
    }

    /**
     * 将 byte 数组指定范围编码为 Base64 字符串。
     *
     * @param source  原始数据
     * @param off     起始偏移
     * @param len     数据长度
     * @param urlSafe 是否使用 URL 安全编码
     * @return Base64 编码字符串
     */
    public static String encodeBytes(byte[] source, int off, int len, boolean urlSafe) {
        if (source == null) {
            throw new NullPointerException("source array is null");
        }
        if (off < 0 || len < 0 || off + len > source.length) {
            throw new IllegalArgumentException(
                    "Invalid offset/len: off=" + off + ", len=" + len + ", array.length=" + source.length);
        }
        if (len == 0) {
            return "";
        }

        byte[] table = urlSafe ? URL_ENCODE_TABLE : ENCODE_TABLE;

        // 精确计算输出长度：每 3 字节 → 4 字符，不足 3 字节补 padding
        int encLen = ((len + 2) / 3) * 4;
        byte[] out = new byte[encLen];

        int s = off;        // 源数组读取位置
        int d = 0;          // 目标数组写入位置
        int end = off + len; // 源数组结束位置

        // 每次处理 3 字节 → 4 字符
        while (s + 3 <= end) {
            int b0 = source[s] & 0xFF;
            int b1 = source[s + 1] & 0xFF;
            int b2 = source[s + 2] & 0xFF;

            out[d]     = table[(b0 >>> 2)];
            out[d + 1] = table[((b0 & 0x03) << 4) | (b1 >>> 4)];
            out[d + 2] = table[((b1 & 0x0F) << 2) | (b2 >>> 6)];
            out[d + 3] = table[b2 & 0x3F];

            s += 3;
            d += 4;
        }

        // 处理剩余字节
        int remaining = end - s;
        if (remaining == 1) {
            int b0 = source[s] & 0xFF;
            out[d]     = table[(b0 >>> 2)];
            out[d + 1] = table[(b0 & 0x03) << 4];
            out[d + 2] = PAD;
            out[d + 3] = PAD;
        } else if (remaining == 2) {
            int b0 = source[s] & 0xFF;
            int b1 = source[s + 1] & 0xFF;
            out[d]     = table[(b0 >>> 2)];
            out[d + 1] = table[((b0 & 0x03) << 4) | (b1 >>> 4)];
            out[d + 2] = table[(b1 & 0x0F) << 2];
            out[d + 3] = PAD;
        }

        return new String(out, 0, encLen, StandardCharsets.US_ASCII);
    }

    /**
     * 将 byte 数组编码为 Base64 byte 数组。
     *
     * @param source 原始数据
     * @return Base64 编码后的 byte 数组
     */
    public static byte[] encodeBytesToBytes(byte[] source) {
        return encodeBytesToBytes(source, 0, source.length);
    }

    /**
     * 将 byte 数组指定范围编码为 Base64 byte 数组。
     *
     * @param source 原始数据
     * @param off    起始偏移
     * @param len    数据长度
     * @return Base64 编码后的 byte 数组
     */
    public static byte[] encodeBytesToBytes(byte[] source, int off, int len) {
        if (source == null) {
            throw new NullPointerException("source array is null");
        }
        if (off < 0 || len < 0 || off + len > source.length) {
            throw new IllegalArgumentException(
                    "Invalid offset/len: off=" + off + ", len=" + len + ", array.length=" + source.length);
        }

        int encLen = ((len + 2) / 3) * 4;
        byte[] out = new byte[encLen];

        int s = off;
        int d = 0;
        int end = off + len;

        while (s + 3 <= end) {
            int b0 = source[s] & 0xFF;
            int b1 = source[s + 1] & 0xFF;
            int b2 = source[s + 2] & 0xFF;

            out[d]     = ENCODE_TABLE[(b0 >>> 2)];
            out[d + 1] = ENCODE_TABLE[((b0 & 0x03) << 4) | (b1 >>> 4)];
            out[d + 2] = ENCODE_TABLE[((b1 & 0x0F) << 2) | (b2 >>> 6)];
            out[d + 3] = ENCODE_TABLE[b2 & 0x3F];

            s += 3;
            d += 4;
        }

        int remaining = end - s;
        if (remaining == 1) {
            int b0 = source[s] & 0xFF;
            out[d]     = ENCODE_TABLE[(b0 >>> 2)];
            out[d + 1] = ENCODE_TABLE[(b0 & 0x03) << 4];
            out[d + 2] = PAD;
            out[d + 3] = PAD;
        } else if (remaining == 2) {
            int b0 = source[s] & 0xFF;
            int b1 = source[s + 1] & 0xFF;
            out[d]     = ENCODE_TABLE[(b0 >>> 2)];
            out[d + 1] = ENCODE_TABLE[((b0 & 0x03) << 4) | (b1 >>> 4)];
            out[d + 2] = ENCODE_TABLE[(b1 & 0x0F) << 2];
            out[d + 3] = PAD;
        }

        return out;
    }

    // ===================== 解码 =====================

    /**
     * 解码 Base64 字符串为 byte 数组。
     *
     * @param s Base64 编码字符串
     * @return 解码后的 byte 数组
     */
    public static byte[] decode(String s) {
        if (s == null) {
            throw new NullPointerException("input string is null");
        }
        return decode(s.getBytes(StandardCharsets.US_ASCII), 0, s.length(), false);
    }

    /**
     * 解码 Base64 byte 数组。
     *
     * @param source Base64 编码数据
     * @return 解码后的 byte 数组
     */
    public static byte[] decode(byte[] source) {
        return decode(source, 0, source.length, false);
    }

    /**
     * 解码 Base64 字符串为 byte 数组。
     *
     * @param s       Base64 编码字符串
     * @param options 编码选项（如 URL_SAFE = 16）
     * @return 解码后的 byte 数组
     */
    public static byte[] decode(String s, int options) {
        if (s == null) {
            throw new NullPointerException("input string is null");
        }
        boolean urlSafe = (options & 16) != 0;
        return decode(s.getBytes(StandardCharsets.US_ASCII), 0, s.length(), urlSafe);
    }

    /**
     * 解码 Base64 byte 数组。
     *
     * @param source  Base64 编码数据
     * @param off     起始偏移
     * @param len     数据长度
     * @param options 编码选项
     * @return 解码后的 byte 数组
     */
    public static byte[] decode(byte[] source, int off, int len, int options) {
        boolean urlSafe = (options & 16) != 0;
        return decode(source, off, len, urlSafe);
    }

    /**
     * 解码 Base64 byte 数组的核心实现。
     *
     * @param source  Base64 编码数据
     * @param off     起始偏移
     * @param len     数据长度
     * @param urlSafe 是否使用 URL 安全解码
     * @return 解码后的 byte 数组
     */
    private static byte[] decode(byte[] source, int off, int len, boolean urlSafe) {
        if (source == null) {
            throw new NullPointerException("source array is null");
        }
        if (off < 0 || len < 0 || off + len > source.length) {
            throw new IllegalArgumentException(
                    "Invalid offset/len: off=" + off + ", len=" + len + ", array.length=" + source.length);
        }
        if (len == 0) {
            return new byte[0];
        }

        byte[] table = urlSafe ? URL_DECODE_TABLE : DECODE_TABLE;

        // 统计 padding 数量以精确计算输出长度
        int padCount = 0;
        if (source[off + len - 1] == PAD) {
            padCount++;
            if (len > 1 && source[off + len - 2] == PAD) {
                padCount++;
            }
        }

        // 精确计算输出长度：每 4 字符 → 3 字节，减去 padding
        int encLen = len - padCount;
        // 跳过尾部的空白和 padding 来计算有效编码字符数
        int outLen = (encLen / 4) * 3 + switchRemainder(encLen % 4);
        byte[] out = new byte[outLen];

        int s = off;
        int d = 0;
        int end = off + len;

        // 每次处理 4 字符 → 3 字节
        while (s + 4 <= end) {
            int c0 = table[source[s] & 0x7F];
            int c1 = table[source[s + 1] & 0x7F];
            int c2 = (source[s + 2] == PAD) ? 0 : table[source[s + 2] & 0x7F];
            int c3 = (source[s + 3] == PAD) ? 0 : table[source[s + 3] & 0x7F];

            // 跳过空白字符
            if (c0 == -2 || c1 == -2) {
                s++;
                continue;
            }

            if (c0 < 0 || c1 < 0) {
                throw new IllegalArgumentException(
                        "Invalid Base64 character: " + (source[s] & 0xFF) + " at position " + (s - off));
            }

            int combined = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;

            out[d]     = (byte) (combined >>> 16);
            out[d + 1] = (byte) (combined >>> 8);
            out[d + 2] = (byte) combined;

            // 根据 padding 调整实际写入字节数
            if (source[s + 2] == PAD) {
                d += 1;
            } else if (source[s + 3] == PAD) {
                d += 2;
            } else {
                d += 3;
            }

            s += 4;
        }

        // 如果输出数组有剩余空间，精确裁剪
        if (d < outLen) {
            byte[] result = new byte[d];
            System.arraycopy(out, 0, result, 0, d);
            return result;
        }
        return out;
    }

    /**
     * 计算余数对应的输出字节数
     */
    private static int switchRemainder(int r) {
        // 0 → 0, 2 → 1, 3 → 2（Base64 编码中余数不会为 1）
        return (r * 3) / 4;
    }
}
