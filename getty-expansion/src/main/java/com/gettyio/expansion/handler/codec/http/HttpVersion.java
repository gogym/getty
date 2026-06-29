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
package com.gettyio.expansion.handler.codec.http;

/**
 * HTTP 协议版本。
 * <p>
 * 表示 HTTP 协议的版本号，如 HTTP/1.0 和 HTTP/1.1。
 * 内置常用版本常量，同时支持自定义版本解析。
 * 解析采用 indexOf 手动拆分，避免正则表达式开销。
 * </p>
 *
 * @author gogym
 */
public class HttpVersion implements Comparable<HttpVersion> {

    /** HTTP/1.0 版本字符串常量 */
    private static final String HTTP_1_0_STRING = "HTTP/1.0";
    /** HTTP/1.1 版本字符串常量 */
    private static final String HTTP_1_1_STRING = "HTTP/1.1";

    /**
     * HTTP/1.0
     */
    public static final HttpVersion HTTP_1_0 = new HttpVersion(HTTP_1_0_STRING, false);

    /**
     * HTTP/1.1
     */
    public static final HttpVersion HTTP_1_1 = new HttpVersion(HTTP_1_1_STRING, true);


    /**
     * 根据文本解析 HTTP 版本。
     * <p>
     * 优先匹配内置常量（HTTP/1.0、HTTP/1.1），未匹配时创建新实例。
     * </p>
     *
     * @param text 版本文本，例如 "HTTP/1.1"
     * @return 对应的 HttpVersion 对象
     * @throws NullPointerException     当 text 为 null 时抛出
     * @throws IllegalArgumentException 当 text 为空或格式不合法时抛出
     */
    public static HttpVersion valueOf(String text) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim();

        if (text.isEmpty()) {
            throw new IllegalArgumentException("text is empty (possibly HTTP/0.9)");
        }
        HttpVersion version = version0(text);
        if (version == null) {
            version = new HttpVersion(text, true);
        }
        return version;
    }

    /**
     * 快速匹配内置常量版本（HTTP/1.0 和 HTTP/1.1）。
     *
     * @param text 版本文本
     * @return 匹配的常量实例，未匹配时返回 null
     */
    private static HttpVersion version0(String text) {
        if (HTTP_1_1_STRING.equals(text)) {
            return HTTP_1_1;
        }
        if (HTTP_1_0_STRING.equals(text)) {
            return HTTP_1_0;
        }
        return null;
    }

    /** 协议名称，例如 "HTTP" */
    private final String protocolName;
    /** 主版本号，例如 1 */
    private final int majorVersion;
    /** 次版本号，例如 1 */
    private final int minorVersion;
    /** 完整版本文本，例如 "HTTP/1.1" */
    private final String text;
    /** 是否默认保持连接（HTTP/1.1 默认为 true） */
    private final boolean keepAliveDefault;


    /**
     * 根据版本文本创建 HttpVersion 实例。
     * <p>
     * 解析格式为 "PROTOCOL/major.minor"，例如 "HTTP/1.1"。
     * 使用 indexOf 手动拆分，避免正则表达式开销。
     * </p>
     *
     * @param text              版本文本
     * @param keepAliveDefault  是否默认保持连接
     * @throws NullPointerException     当 text 为 null 时抛出
     * @throws IllegalArgumentException 当格式不合法时抛出
     */
    public HttpVersion(String text, boolean keepAliveDefault) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim().toUpperCase();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty text");
        }

        // 解析格式: "PROTOCOL/major.minor"，例如 "HTTP/1.1"
        int slashIdx = text.indexOf('/');
        if (slashIdx < 1) {
            throw new IllegalArgumentException("invalid version format: " + text);
        }
        int dotIdx = text.indexOf('.', slashIdx + 1);
        if (dotIdx < 0 || dotIdx >= text.length() - 1) {
            throw new IllegalArgumentException("invalid version format: " + text);
        }

        protocolName = text.substring(0, slashIdx);
        try {
            majorVersion = Integer.parseInt(text.substring(slashIdx + 1, dotIdx));
            minorVersion = Integer.parseInt(text.substring(dotIdx + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid version format: " + text, e);
        }
        this.text = protocolName + '/' + majorVersion + '.' + minorVersion;
        this.keepAliveDefault = keepAliveDefault;
    }

    /**
     * 获取协议名称，例如 {@code "HTTP"}。
     *
     * @return 协议名称
     */
    public String protocolName() {
        return protocolName;
    }

    /**
     * 获取主版本号，例如 {@code 1}。
     *
     * @return 主版本号
     */
    public int majorVersion() {
        return majorVersion;
    }

    /**
     * 获取次版本号，例如 {@code 0}。
     *
     * @return 次版本号
     */
    public int minorVersion() {
        return minorVersion;
    }

    /**
     * 获取完整的协议版本文本，例如 {@code "HTTP/1.0"}。
     *
     * @return 版本文本
     */
    public String text() {
        return text;
    }

    /**
     * 是否默认保持连接。
     * <p>
     * HTTP/1.1 默认返回 true，HTTP/1.0 默认返回 false。
     * 除非 {@code "Connection"} 头部显式设置为 {@code "close"}，否则连接保持。
     * </p>
     *
     * @return 是否默认保持连接
     */
    public boolean isKeepAliveDefault() {
        return keepAliveDefault;
    }

    /**
     * 返回完整的协议版本文本，例如 {@code "HTTP/1.0"}。
     *
     * @return 版本文本
     */
    @Override
    public String toString() {
        return text();
    }

    /**
     * 计算哈希值，基于协议名称和版本号。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return (protocolName().hashCode() * 31 + majorVersion()) * 31 +
                minorVersion();
    }

    /**
     * 比较两个版本是否相等。基于协议名称、主版本号和次版本号判断。
     *
     * @param o 比较对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpVersion)) {
            return false;
        }

        HttpVersion that = (HttpVersion) o;
        return minorVersion() == that.minorVersion() &&
                majorVersion() == that.majorVersion() &&
                protocolName().equals(that.protocolName());
    }

    /**
     * 按协议名称、主版本号、次版本号依次比较。
     *
     * @param o 比较对象
     * @return 负数表示小于，0 表示相等，正数表示大于
     */
    @Override
    public int compareTo(HttpVersion o) {
        int v = protocolName().compareTo(o.protocolName());
        if (v != 0) {
            return v;
        }

        v = majorVersion() - o.majorVersion();
        if (v != 0) {
            return v;
        }
        return minorVersion() - o.minorVersion();
    }

}
