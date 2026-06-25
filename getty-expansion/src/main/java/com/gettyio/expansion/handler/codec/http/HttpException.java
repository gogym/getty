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

import com.gettyio.expansion.handler.codec.http.response.HttpResponseStatus;

/**
 * HTTP 异常。
 * <p>
 * 携带 HTTP 状态码的异常，用于在 HTTP 处理流程中传递错误信息。
 * </p>
 *
 * @author gogym
 */
public class HttpException extends Exception {
    private final HttpResponseStatus status;
    private final String message;

    public HttpException(HttpResponseStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpException(HttpResponseStatus status) {
        this(status, null);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

}
