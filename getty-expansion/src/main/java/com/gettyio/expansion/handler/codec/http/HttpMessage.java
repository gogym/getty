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
 * HttpMessage.java
 *
 * @description:http消息封装
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */

public class HttpMessage {

    /**
     * http版本
     */
    protected HttpVersion httpVersion;
    /**
     * http headers
     */
    protected HttpHeaders httpHeaders = new HttpHeaders();
    /**
     * http body
     */
    protected HttpBody httpBody = new HttpBody();
    /**
     * 读取状态
     */
    private int readStatus;

    public StringBuilder sb = new StringBuilder();

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
