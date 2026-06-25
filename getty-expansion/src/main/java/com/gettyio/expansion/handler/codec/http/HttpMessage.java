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
 * 封装 HTTP 版本号、头部、消息体和解析状态，
 * 作为 {@link com.gettyio.expansion.handler.codec.http.request.HttpRequest}
 * 和 {@link com.gettyio.expansion.handler.codec.http.response.HttpResponse} 的公共父类。
 * </p>
 *
 * @author gogym
 */
public class HttpMessage {

    /** HTTP 协议版本 */
    protected HttpVersion httpVersion;
    /** HTTP 头部集合 */
    protected HttpHeaders httpHeaders = new HttpHeaders();
    /** HTTP 消息体 */
    protected HttpBody httpBody = new HttpBody();
    /** 解析状态机状态 */
    private int readStatus;
    /** 行/头部解析用临时缓冲区 */
    private final StringBuilder sb = new StringBuilder();

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public String getHeader(String name) {
        return this.httpHeaders.getHeader(name);
    }

    public List<String> getHeaders(String name) {
        return this.httpHeaders.getHeaders(name);
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return this.httpHeaders.getHeaders();
    }


    public void setHeader(String name, Object value) {
        this.httpHeaders.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        this.httpHeaders.setHeader(name, values);
    }

    public void addHeader(final String name, final Object value) {
        this.httpHeaders.addHeader(name, value);
    }

    public void removeHeader(final String name) {
        this.httpHeaders.removeHeader(name);
    }

    public HttpBody getHttpBody() {
        return httpBody;
    }

    public void setHttpBody(HttpBody httpBody) {
        this.httpBody = httpBody;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }

    public StringBuilder getSb() {
        return sb;
    }
}
