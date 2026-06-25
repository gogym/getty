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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 高性能 MD5 哈希工具类。
 * <p>
 * 基于 JDK 内置的 {@link MessageDigest} 实现，利用 JVM 对密码学原语的硬件加速优化，
 * 在绝大多数平台上均能获得接近或超越手工实现的性能。
 * <p>
 * 提供两种使用方式：
 * <ul>
 *   <li><b>静态方法</b>：适用于一次性哈希计算，如 {@link #md5(byte[])}、{@link #md5Hex(String)}</li>
 *   <li><b>实例方法</b>：适用于流式/增量哈希计算，通过 {@link #update(byte[])} 多次喂入数据，
 *       最后调用 {@link #digest()} 或 {@link #asHex()} 获取结果</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式一：静态方法 —— 一次性计算
 * String hex = MD5.md5Hex("hello world");
 *
 * // 方式二：实例方法 —— 增量计算
 * MD5 md5 = new MD5();
 * md5.update(part1);
 * md5.update(part2);
 * String hash = md5.asHex();
 *
 * // 方式三：计算文件哈希
 * byte[] hash = MD5.md5(new File("data.bin"));
 * }</pre>
 *
 * <p><b>线程安全性</b>：实例方法非线程安全，每个线程应使用独立的 {@code MD5} 实例。
 * 静态方法是线程安全的。
 *
 * @author Getty Project
 * @see MessageDigest
 */
public final class MD5 {

    /** MD5 哈希输出长度（字节） */
    public static final int HASH_LENGTH = 16;

    /** 十六进制字符查找表（小写） */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** 文件读取缓冲区大小：8KB */
    private static final int FILE_BUFFER_SIZE = 8192;

    /** 内部 MessageDigest 实例（每个 MD5 对象独立持有） */
    private final MessageDigest digest;

    // ==================== 构造函数 ====================

    /**
     * 创建一个新的 MD5 哈希计算器。
     * <p>
     * 内部初始化一个 {@link MessageDigest} 实例，可通过 {@link #update(byte[])} 增量喂入数据。
     */
    public MD5() {
        this.digest = newMessageDigest();
    }

    /**
     * 创建 MD5 实例并用给定数据的字节表示初始化哈希。
     *
     * @param data 初始数据，调用 {@code data.toString()} 后以平台默认字符集编码为字节
     */
    public MD5(Object data) {
        this();
        update(data.toString());
    }

    // ==================== 实例方法（增量哈希） ====================

    /**
     * 向哈希计算中追加字节数组。
     *
     * @param data 要追加的字节数组，不可为 {@code null}
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(byte[] data) {
        digest.update(data);
        return this;
    }

    /**
     * 向哈希计算中追加字节数组的指定部分。
     *
     * @param data   源字节数组
     * @param offset 起始偏移量
     * @param length 要追加的字节数
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(byte[] data, int offset, int length) {
        digest.update(data, offset, length);
        return this;
    }

    /**
     * 向哈希计算中追加单个字节（仅使用低 8 位）。
     *
     * @param b 要追加的字节
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(byte b) {
        digest.update(b);
        return this;
    }

    /**
     * 向哈希计算中追加整数值（仅使用低 8 位，等同于 {@code update((byte)(value & 0xFF))}）。
     *
     * @param value 整数值
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(int value) {
        digest.update((byte) (value & 0xFF));
        return this;
    }

    /**
     * 向哈希计算中追加字符串（使用平台默认字符集编码）。
     *
     * @param s 要追加的字符串，不可为 {@code null}
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(String s) {
        digest.update(s.getBytes());
        return this;
    }

    /**
     * 向哈希计算中追加字符串（使用指定字符集编码）。
     *
     * @param s       要追加的字符串
     * @param charset 字符编码
     * @return 当前实例（支持链式调用）
     */
    public MD5 update(String s, Charset charset) {
        digest.update(s.getBytes(charset));
        return this;
    }

    /**
     * 完成哈希计算并返回 16 字节的 MD5 摘要。
     * <p>
     * 调用后内部状态被重置，可重新使用此实例进行下一次哈希计算。
     *
     * @return 16 字节的 MD5 哈希值
     */
    public byte[] digest() {
        return digest.digest();
    }

    /**
     * 完成哈希计算并返回 32 字符的小写十六进制字符串。
     * <p>
     * 调用后内部状态被重置，可重新使用此实例进行下一次哈希计算。
     *
     * @return 32 字符的小写十六进制 MD5 哈希字符串
     */
    public String asHex() {
        return toHex(digest.digest());
    }

    /**
     * 重置内部状态，使此实例可重新用于新的哈希计算。
     */
    public void reset() {
        digest.reset();
    }

    // ==================== 静态方法（一次性哈希） ====================

    /**
     * 计算字节数组的 MD5 哈希值（16 字节）。
     *
     * @param data 输入数据
     * @return 16 字节的 MD5 哈希值
     */
    public static byte[] md5(byte[] data) {
        return newMessageDigest().digest(data);
    }

    /**
     * 计算字符串的 MD5 哈希值（16 字节），使用 UTF-8 编码。
     *
     * @param text 输入字符串
     * @return 16 字节的 MD5 哈希值
     */
    public static byte[] md5(String text) {
        return md5(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 哈希并返回 32 字符小写十六进制字符串，使用 UTF-8 编码。
     *
     * @param data 输入数据
     * @return 32 字符小写十六进制 MD5 字符串
     */
    public static String md5Hex(byte[] data) {
        return toHex(md5(data));
    }

    /**
     * 计算字符串的 MD5 哈希并返回 32 字符小写十六进制字符串。
     *
     * @param text 输入字符串
     * @return 32 字符小写十六进制 MD5 字符串
     */
    public static String md5Hex(String text) {
        return toHex(md5(text));
    }

    /**
     * 计算文件的 MD5 哈希值（16 字节）。
     * <p>
     * 使用 8KB 缓冲区分块读取文件，适用于任意大小的文件。
     *
     * @param file 输入文件，必须存在且可读
     * @return 16 字节的 MD5 哈希值
     * @throws IOException 如果文件不存在或读取过程中发生 I/O 错误
     */
    public static byte[] md5(File file) throws IOException {
        MessageDigest md = newMessageDigest();
        byte[] buf = new byte[FILE_BUFFER_SIZE];
        try (InputStream in = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                md.update(buf, 0, bytesRead);
            }
        }
        return md.digest();
    }

    /**
     * 计算文件的 MD5 哈希并返回 32 字符小写十六进制字符串。
     *
     * @param file 输入文件
     * @return 32 字符小写十六进制 MD5 字符串
     * @throws IOException 如果文件不存在或读取过程中发生 I/O 错误
     */
    public static String md5Hex(File file) throws IOException {
        return toHex(md5(file));
    }

    /**
     * 计算输入流的 MD5 哈希值（16 字节）。
     * <p>
     * <b>注意</b>：此方法不会关闭输入流，调用方负责关闭。
     *
     * @param in 输入流
     * @return 16 字节的 MD5 哈希值
     * @throws IOException 如果读取过程中发生 I/O 错误
     */
    public static byte[] md5(InputStream in) throws IOException {
        MessageDigest md = newMessageDigest();
        byte[] buf = new byte[FILE_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buf)) != -1) {
            md.update(buf, 0, bytesRead);
        }
        return md.digest();
    }

    // ==================== 比较工具方法 ====================

    /**
     * 比较两个 MD5 哈希字节数组是否相等。
     * <p>
     * 比较前 16 字节（或较短数组的全部字节），采用常量时间比较以防止时序攻击。
     *
     * @param hash1 第一个哈希值
     * @param hash2 第二个哈希值
     * @return 两个哈希值相等返回 {@code true}
     */
    public static boolean equals(byte[] hash1, byte[] hash2) {
        if (hash1 == null) return hash2 == null;
        if (hash2 == null) return false;
        int len = Math.min(Math.min(hash1.length, hash2.length), HASH_LENGTH);
        int result = 0;
        for (int i = 0; i < len; i++) {
            result |= hash1[i] ^ hash2[i];
        }
        return result == 0;
    }

    // ==================== 十六进制转换 ====================

    /**
     * 将字节数组转换为小写十六进制字符串。
     *
     * @param bytes 输入字节数组
     * @return 对应的十六进制字符串（长度为 {@code bytes.length * 2}）
     */
    public static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            hex[j++] = HEX_CHARS[b >>> 4];
            hex[j++] = HEX_CHARS[b & 0x0F];
        }
        return new String(hex);
    }

    // ==================== 内部工具 ====================

    /**
     * 创建一个新的 MD5 {@link MessageDigest} 实例。
     *
     * @return 新的 MessageDigest 实例
     * @throws RuntimeException 如果当前 JVM 不支持 MD5 算法（理论上不会发生）
     */
    private static MessageDigest newMessageDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be available in every JDK implementation
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
