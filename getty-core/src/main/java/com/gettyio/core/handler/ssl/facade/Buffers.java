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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
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
     * 为 unwrap 操作准备入站缓冲区（ByteBuffer 版本）。
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
     * 为 unwrap 操作准备入站缓冲区（PooledByteBuffer 版本）。
     * <p>通过 System.arraycopy 直接从 PooledByteBuffer 底层数组拷贝到内部密文缓冲区，
     * 消除中间 byte[] 分配，实现单次拷贝。SSLEngine 仅接触隔离的内部缓冲区。</p>
     * <p>如有缓存的未处理数据，一并合并到密文缓冲区中。</p>
     *
     * @param src 待解密的密文数据（PooledByteBuffer）
     */
    void prepareForUnwrap(PooledByteBuffer src) {
        clear(BufferType.IN_CIPHER, BufferType.IN_PLAIN);
        int len = src.readableBytes();

        // 优先检查缓存：有缓存时需将缓存数据与新数据合并
        if (!unwrapCache.hasRemaining()) {
            // 无缓存快速路径：直接从 PooledByteBuffer 拷贝到内部密文缓冲区
            ByteBuffer cipherBuf = growIfNecessary(BufferType.IN_CIPHER, len);
            System.arraycopy(src.array(), src.arrayOffset() + src.readerIndex(),
                    cipherBuf.array(), cipherBuf.arrayOffset(), len);
            src.readerIndex(src.readerIndex() + len);
            cipherBuf.position(len);
            cipherBuf.flip();
            return;
        }

        // 有缓存路径：先拷贝缓存数据，再拷贝新数据
        ByteBuffer cacheData = unwrapCache.get();
        int cacheLen = cacheData.remaining();
        ByteBuffer cipherBuf = growIfNecessary(BufferType.IN_CIPHER, cacheLen + len);
        // 拷贝缓存数据到密文缓冲区
        System.arraycopy(cacheData.array(), cacheData.arrayOffset() + cacheData.position(),
                cipherBuf.array(), cipherBuf.arrayOffset(), cacheLen);
        // 拷贝 PooledByteBuffer 数据到密文缓冲区（紧接缓存数据之后）
        System.arraycopy(src.array(), src.arrayOffset() + src.readerIndex(),
                cipherBuf.array(), cipherBuf.arrayOffset() + cacheLen, len);
        src.readerIndex(src.readerIndex() + len);
        unwrapCache.clear();
        cipherBuf.position(cacheLen + len);
        cipherBuf.flip();
    }

    /**
     * 为 wrap 操作准备出站缓冲区（ByteBuffer 版本）。
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

    /**
     * 为 wrap 操作准备出站缓冲区（PooledByteBuffer 版本）。
     * <p>通过 System.arraycopy 直接从 PooledByteBuffer 底层数组拷贝到内部明文缓冲区，
     * 消除中间 byte[] 分配，实现单次拷贝。SSLEngine 仅接触隔离的内部缓冲区。</p>
     *
     * @param src 待加密的明文数据（PooledByteBuffer）
     */
    void prepareForWrap(PooledByteBuffer src) {
        clear(BufferType.OUT_PLAIN, BufferType.OUT_CIPHER);
        int len = src.readableBytes();
        ByteBuffer plainBuf = growIfNecessary(BufferType.OUT_PLAIN, len);
        System.arraycopy(src.array(), src.arrayOffset() + src.readerIndex(),
                plainBuf.array(), plainBuf.arrayOffset(), len);
        src.readerIndex(src.readerIndex() + len);
        plainBuf.position(len);
        plainBuf.flip();
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
     * <p>调用前缓冲区已通过 clear() 处于写模式（pos=0, limit=capacity），
     * 因此扩容后新缓冲区也应保持写模式，不使用 {@link BufferUtils#copy}（其内部 flip 会破坏写模式）。</p>
     */
    private ByteBuffer growIfNecessary(BufferType type, int dataSize) {
        ByteBuffer buf = get(type);
        if (buf.remaining() < dataSize) {
            ByteBuffer resized = ByteBuffer.allocate(Math.max(buf.capacity() * 2, dataSize));
            assign(type, resized);
            return resized;
        }
        return buf;
    }
}
