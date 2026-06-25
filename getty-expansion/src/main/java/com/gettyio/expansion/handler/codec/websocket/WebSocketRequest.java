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
package com.gettyio.expansion.handler.codec.websocket;


import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 握手请求信息载体。
 * <p>
 * 保存客户端 HTTP 升级请求中解析出的各字段，
 * 包括请求 URI、主机、源、密钥、版本号等。
 * 仅在 {@link WebSocketHandShake} 内部使用。
 * </p>
 *
 * @author gogym
 */
class WebSocketRequest {

    /** 请求 URI */
    private String requestUri;
    /** 主机地址 */
    private String host;
    /** 源地址 */
    private String origin;
    /** Cookie */
    private String cookie;
    /** 是否为 WebSocket 升级请求 */
    private boolean upgrade = false;
    /** 是否包含 Connection: Upgrade 头 */
    private boolean connection = false;
    /** Hixie-76 Key1 值 */
    private long key1;
    /** Hixie-76 Key2 值 */
    private long key2;
    /** 握手响应摘要（MD5 或 SHA-1+Base64） */
    private String digest;
    /** Sec-WebSocket-Version，默认 0 */
    private int secVersion = 0;
    /** 请求头映射 */
    private final Map<String, String> headers = new HashMap<>(8);
    /** 解析状态机状态 */
    private int readStatus;
    /** 行/头部解析用临时缓冲区 */
    private final StringBuilder sb = new StringBuilder();

    public boolean getConnection() {
        return connection;
    }

    public void setConnection(boolean connection) {
        this.connection = connection;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getKey1() {
        return key1;
    }

    public void setKey1(long key1) {
        this.key1 = key1;
    }

    public long getKey2() {
        return key2;
    }

    public void setKey2(long key2) {
        this.key2 = key2;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public boolean getUpgrade() {
        return upgrade;
    }

    public void setUpgrade(boolean upgrade) {
        this.upgrade = upgrade;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public int getSecVersion() {
        return secVersion;
    }

    public void setSecVersion(int secVersion) {
        this.secVersion = secVersion;
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

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void putHeader(String key, String value) {
        this.headers.put(key, value);
    }
}
