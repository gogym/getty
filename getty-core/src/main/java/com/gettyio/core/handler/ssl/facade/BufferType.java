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
package com.gettyio.core.handler.ssl.facade;

/**
 * SSL 缓冲区类型标识。
 * <p>
 * 对应 SSLEngine 操作所需的四个缓冲区：
 * <ul>
 *   <li>{@link #IN_PLAIN} — 入站明文缓冲区（peerAppData），unwrap 的目标</li>
 *   <li>{@link #IN_CIPHER} — 入站密文缓冲区（peerNetData），unwrap 的源</li>
 *   <li>{@link #OUT_PLAIN} — 出站立文缓冲区（myAppData），wrap 的源</li>
 *   <li>{@link #OUT_CIPHER} — 出站密文缓冲区（myNetData），wrap 的目标</li>
 * </ul>
 */
enum BufferType {
    /** 入站明文（解密后的应用数据） */
    IN_PLAIN,
    /** 入站密文（对端发送的加密数据） */
    IN_CIPHER,
    /** 出站立文（待加密的应用数据） */
    OUT_PLAIN,
    /** 出站密文（加密后待发送的数据） */
    OUT_CIPHER
}
