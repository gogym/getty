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

import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;

/**
 * SSL 缓冲区管理器。
 * <p>
 * 封装 SSLEngine 操作所需的四个缓冲区和一个 unwrap 缓存区：
 * <ul>
 *   <li>IN_PLAIN (peerAppData) — 入站明文，unwrap 的目标缓冲区</li>
 *   <li>IN_CIPHER (peerNetData) — 入站密文，unwrap 的源缓冲区</li>
 *   <li>OUT_PLAIN (myAppData) — 出站立文，wrap 的源缓冲区</li>
 *   <li>OUT_CIPHER (myNetData) — 出站密文，wrap 的目标缓冲区</li>
 * </ul>
 * </p>
 *
 * <p>unwrap 缓存区用于保存因 BUFFER_UNDERFLOW 而未完全消费的密文数据，
 * 与后续到达的数据拼接后继续解密。</p>
 *
 * <p><b>注意：</b>这些缓冲区由 SSLEngine 直接操作，不应被外部复用。
 * 缓冲区会在 BUFFER_OVERFLOW 时自动扩容至 SSLSession 推荐的尺寸。</p>
 */
class Buffers {

    private ByteBuffer peerApp;    // IN_PLAIN
    private ByteBuffer myApp;      // OUT_PLAIN
    private ByteBuffer peerNet;    // IN_CIPHER
    private ByteBuffer myNet;      // OUT_CIPHER
    private final AppendableBuffer unwrapCache;
    private final SSLSession session;

    /**
     * 根据 SSL 会话的推荐缓冲区大小创建实例。
     *
     * @param session SSL 会话，提供缓冲区的推荐尺寸
     */
    Buffers(SSLSession session) {
        this.session = session;
        allocate();
        this.unwrapCache = new AppendableBuffer();
    }

    /**
     * 获取指定类型的缓冲区。
     */
    ByteBuffer get(BufferType type) {
        switch (type) {
            case IN_PLAIN:  return peerApp;
            case IN_CIPHER: return peerNet;
            case OUT_PLAIN: return myApp;
            case OUT_CIPHER: return myNet;
            default: throw new IllegalArgumentException("Unknown buffer type: " + type);
        }
    }

    /**
     * 按 SSL 会话推荐的尺寸扩容指定缓冲区。
     */
    void grow(BufferType type) {
        switch (type) {
            case IN_PLAIN:
                assign(type, growTo(type, session.getApplicationBufferSize()));
                break;
            case IN_CIPHER:
                assign(type, growTo(type, session.getPacketBufferSize()));
                break;
            case OUT_CIPHER:
                assign(type, growTo(type, session.getPacketBufferSize()));
                break;
            case OUT_PLAIN:
                // OUT_PLAIN 不会发生 BUFFER_OVERFLOW
                break;
        }
    }

    /**
     * 为 unwrap 操作准备入站缓冲区。
     * <p>清空入站密文和明文缓冲区，将数据加载到密文缓冲区中。</p>
     *
     * @param data 待解密的密文数据，null 表示无新数据
     */
    void prepareForUnwrap(ByteBuffer data) {
        clear(BufferType.IN_CIPHER, BufferType.IN_PLAIN);
        if (data != null) {
            ByteBuffer cipherBuf = growIfNecessary(BufferType.IN_CIPHER, data.limit());
            cipherBuf.put(data);
            cipherBuf.flip();
        }
    }

    /**
     * 为 wrap 操作准备出站缓冲区。
     * <p>清空出站立文和密文缓冲区，将数据加载到明文缓冲区中。</p>
     *
     * @param data 待加密的明文数据，null 表示无新数据
     */
    void prepareForWrap(ByteBuffer data) {
        clear(BufferType.OUT_PLAIN, BufferType.OUT_CIPHER);
        if (data != null) {
            ByteBuffer plainBuf = growIfNecessary(BufferType.OUT_PLAIN, data.limit());
            plainBuf.put(data);
            plainBuf.flip();
        }
    }

    // ---- unwrap 缓存操作 ----

    /**
     * 将新数据与缓存中的旧数据拼接。
     *
     * @param data 新到达的数据，null 时返回缓存数据
     * @return 拼接后的数据（position=0）
     */
    ByteBuffer prependCached(ByteBuffer data) {
        if (data == null) {
            return unwrapCache.get();
        }
        ByteBuffer result = unwrapCache.append(data);
        result.rewind();
        return result;
    }

    /**
     * 缓存未处理的数据。
     */
    void cache(ByteBuffer data) {
        if (data != null) {
            unwrapCache.set(data);
        }
    }

    /**
     * 清空缓存。
     */
    void clearCache() {
        unwrapCache.clear();
    }

    /**
     * 缓存是否为空。
     */
    boolean isCacheEmpty() {
        return !unwrapCache.hasRemaining();
    }

    // ---- 内部方法 ----

    private void allocate() {
        int appSize = session.getApplicationBufferSize();
        int pktSize = session.getPacketBufferSize();
        peerApp = ByteBuffer.allocate(appSize);
        myApp   = ByteBuffer.allocate(appSize);
        peerNet = ByteBuffer.allocate(pktSize);
        myNet   = ByteBuffer.allocate(pktSize);
    }

    private void clear(BufferType source, BufferType destination) {
        get(source).clear();
        get(destination).clear();
    }

    private void assign(BufferType type, ByteBuffer buf) {
        switch (type) {
            case IN_PLAIN:  peerApp = buf; break;
            case IN_CIPHER: peerNet = buf; break;
            case OUT_PLAIN: myApp   = buf; break;
            case OUT_CIPHER: myNet   = buf; break;
        }
    }

    /**
     * 将缓冲区扩容到指定尺寸并复制现有数据。
     */
    private ByteBuffer growTo(BufferType type, int newSize) {
        ByteBuffer original = get(type);
        ByteBuffer grown = ByteBuffer.allocate(newSize);
        BufferUtils.copy(original, grown);
        return grown;
    }

    /**
     * 如果缓冲区剩余空间不足，则扩容。
     */
    private ByteBuffer growIfNecessary(BufferType type, int dataSize) {
        ByteBuffer buf = get(type);
        if (buf.position() + dataSize > buf.capacity()) {
            ByteBuffer resized = ByteBuffer.allocate(buf.limit() + dataSize);
            BufferUtils.copy(buf, resized);
            assign(type, resized);
            return resized;
        }
        return buf;
    }
}
