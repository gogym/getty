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
package com.gettyio.expansion.handler.codec.http.response;

import com.gettyio.core.util.DateTimeUtil;
import com.gettyio.expansion.handler.codec.http.HttpHeaders;
import com.gettyio.expansion.handler.codec.http.HttpMessage;
import com.gettyio.expansion.handler.codec.http.HttpVersion;

/**
 * HTTP 响应消息。
 * <p>
 * 封装 HTTP 响应的状态码和原因短语。
 * 继承 {@link HttpMessage} 的头部和消息体功能。
 * </p>
 *
 * @author gogym
 */
public class HttpResponse extends HttpMessage {

    /** 缓存的时间字符串，每秒更新一次 */
    private static volatile String cachedTime = DateTimeUtil.getCurrentTime();
    private static volatile long cachedTimeSecond = System.currentTimeMillis() / 1000;

    /**
     * 获取当前时间字符串，每秒更新一次缓存。
     * <p>
     * 避免高频创建 SimpleDateFormat 结果，通过 volatile + 每秒更新实现线程安全缓存。
     * </p>
     *
     * @return 当前时间字符串
     */
    private static String currentTime() {
        long now = System.currentTimeMillis() / 1000;
        if (now != cachedTimeSecond) {
            cachedTime = DateTimeUtil.getCurrentTime();
            cachedTimeSecond = now;
        }
        return cachedTime;
    }

    /** HTTP 响应状态 */
    private HttpResponseStatus httpResponseStatus;

    /**
     * 创建空的 HTTP 响应对象。
     */
    public HttpResponse() {

    }

    /**
     * 创建指定版本和状态的 HTTP 响应对象。
     * <p>
     * 自动添加 Server 和 Date 头部。
     * </p>
     *
     * @param httpVersion       HTTP 协议版本
     * @param httpResponseStatus HTTP 响应状态
     */
    public HttpResponse(HttpVersion httpVersion, HttpResponseStatus httpResponseStatus) {
        super.httpVersion = httpVersion;
        this.httpResponseStatus = httpResponseStatus;
        addHeader(HttpHeaders.Names.SERVER, "getty");
        addHeader(HttpHeaders.Names.DATE, currentTime());
    }

    /**
     * 获取 HTTP 响应状态。
     *
     * @return 响应状态对象
     */
    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    /**
     * 设置 HTTP 响应状态。
     *
     * @param httpResponseStatus 响应状态对象
     */
    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

}
