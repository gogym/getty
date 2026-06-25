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
package com.gettyio.core.handler.ssl;

/**
 * SSL 握手或通信异常。
 * <p>
 * 当 SSL 握手失败、证书验证失败或 TLS 通信过程中发生错误时抛出。
 * 作为 {@link RuntimeException} 的子类，调用方无需显式捕获。
 * </p>
 *
 * <p><b>注意：</b>此类与 {@code javax.net.ssl.SSLException} 不同，
 * 后者是受检异常且仅在 facade 内部使用。本类是面向应用层的运行时异常。</p>
 */
public class SSLException extends RuntimeException {

    private static final long serialVersionUID = -1464830400709348473L;

    public SSLException() {
    }

    public SSLException(String message) {
        super(message);
    }

    public SSLException(Throwable cause) {
        super(cause);
    }

    public SSLException(String message, Throwable cause) {
        super(message, cause);
    }
}
