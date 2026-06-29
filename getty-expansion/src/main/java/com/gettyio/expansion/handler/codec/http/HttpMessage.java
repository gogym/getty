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

import java.util.List;
import java.util.Map;

/**
 * HTTP 消息封装基类。
 * <p>
 * 封装 HTTP 版本号、头部和消息体，
 * 作为 {@link com.gettyio.expansion.handler.codec.http.request.HttpRequest}
 * 和 {@link com.gettyio.expansion.handler.codec.http.response.HttpResponse} 的公共父类。
 * </p>
 * <p>
 * 注意：解析过程中的临时状态（如 readStatus、StringBuilder）已移至
 * {@link HttpDecodeSerializer.ParseState}，由 Decoder 持有，不属于消息领域模型。
 * </p>
 *
 * @author gogym
 */
public class HttpMessage {

    /** HTTP 协议版本 */
    protected HttpVersion httpVersion;
    /** HTTP 头部集合，使用自定义哈希链表实现，性能优于 HashMap */
    protected HttpHeaders httpHeaders = new HttpHeaders();
    /** HTTP 消息体，包含内容类型、长度和原始字节数据 */
    protected HttpBody httpBody = new HttpBody();

    /**
     * 获取 HTTP 协议版本。
     *
     * @return HTTP 协议版本对象
     */
    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * 设置 HTTP 协议版本。
     *
     * @param httpVersion HTTP 协议版本对象
     */
    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    /**
     * 获取 HTTP 头部对象。
     *
     * @return HTTP 头部集合
     */
    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    /**
     * 设置 HTTP 头部对象（整体替换）。
     *
     * @param httpHeaders 新的头部集合
     */
    public void setHttpHeaders(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    /**
     * 获取指定名称的头部值。如果有多个同名头部，返回第一个。
     *
     * @param name 头部名称（不区分大小写）
     * @return 头部值，不存在时返回 null
     */
    public String getHeader(String name) {
        return this.httpHeaders.getHeader(name);
    }

    /**
     * 获取指定名称的所有头部值。
     *
     * @param name 头部名称
     * @return 头部值列表，不存在时返回空列表
     */
    public List<String> getHeaders(String name) {
        return this.httpHeaders.getHeaders(name);
    }

    /**
     * 获取所有头部条目（创建新列表）。
     *
     * @return 所有头部条目的列表副本
     */
    public List<Map.Entry<String, String>> getHeaders() {
        return this.httpHeaders.getHeaders();
    }

    /**
     * 返回所有头部条目的 Iterable 视图，不创建新列表，适合遍历场景。
     * <p>
     * 编码器等遍历场景应优先使用此方法，避免每次创建 LinkedList。
     * </p>
     *
     * @return 头部条目的可迭代视图
     */
    public Iterable<Map.Entry<String, String>> getHeaderEntries() {
        return this.httpHeaders.entries();
    }

    /**
     * 设置头部（覆盖同名）。如果已存在同名头部，先移除再添加。
     *
     * @param name  头部名称
     * @param value 头部值
     */
    public void setHeader(String name, Object value) {
        this.httpHeaders.setHeader(name, value);
    }

    /**
     * 设置头部的多个值（覆盖同名）。先移除已有同名头部，再依次添加所有值。
     *
     * @param name   头部名称
     * @param values 头部值集合
     */
    public void setHeader(final String name, final Iterable<?> values) {
        this.httpHeaders.setHeader(name, values);
    }

    /**
     * 添加头部（不覆盖同名）。保留已有同名头部，新增一个值。
     *
     * @param name  头部名称
     * @param value 头部值
     */
    public void addHeader(final String name, final Object value) {
        this.httpHeaders.addHeader(name, value);
    }

    /**
     * 移除指定名称的所有头部。
     *
     * @param name 头部名称
     */
    public void removeHeader(final String name) {
        this.httpHeaders.removeHeader(name);
    }

    /**
     * 获取 HTTP 消息体。
     *
     * @return 消息体对象
     */
    public HttpBody getHttpBody() {
        return httpBody;
    }

    /**
     * 设置 HTTP 消息体。
     *
     * @param httpBody 消息体对象
     */
    public void setHttpBody(HttpBody httpBody) {
        this.httpBody = httpBody;
    }
}
