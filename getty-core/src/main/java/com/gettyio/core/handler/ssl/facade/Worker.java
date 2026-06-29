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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * SSL 加密/解密工作器。
 * <p>
 * 封装 {@link SSLEngine} 的 wrap/unwrap 操作，管理缓冲区分配和数据传输。
 * 产生的加密数据和解密数据通过 {@link SSLFacade.SSLDataListener} 回调传递。
 * </p>
 *
 * <p><b>性能说明：</b>枚举比较使用 {@code ==} 而非 {@code .equals()}，
 * 避免虚方法调用开销。</p>
 */
class Worker {

    /** 预分配的空缓冲区常量，用于 unwrap 递归处理缓存数据时作为占位参数，避免每次 allocate(0)。 */
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final SSLEngine engine;
    private final Buffers buffers;
    private SSLFacade.SSLDataListener dataListener;
    private Runnable sessionClosedCallback;

    Worker(SSLEngine engine, Buffers buffers) {
        this.engine = engine;
        this.buffers = buffers;
    }

    /**
     * 设置数据监听器。
     */
    void setDataListener(SSLFacade.SSLDataListener listener) {
        this.dataListener = listener;
    }

    /**
     * 设置会话关闭回调。
     */
    void setSessionClosedCallback(Runnable callback) {
        this.sessionClosedCallback = callback;
    }

    // ---- SSLEngine 委托方法 ----

    void beginHandshake() throws SSLException {
        engine.beginHandshake();
    }

    SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return engine.getHandshakeStatus();
    }

    Runnable getDelegatedTask() {
        return engine.getDelegatedTask();
    }

    /**
     * 是否有缓存的未处理数据（用于握手阶段判断）。
     */
    boolean hasPendingData() {
        return !buffers.isCacheEmpty();
    }

    // ---- 核心操作 ----

    /**
     * 加密明文数据（ByteBuffer 版本）。
     * <p>将明文加载到出站缓冲区，调用 SSLEngine.wrap()，
     * 并将产生的密文通过 {@link SSLFacade.SSLDataListener#onWrappedData} 回调传递。</p>
     *
     * @param plainData 待加密的明文，null 表示仅执行握手 wrap
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult wrap(ByteBuffer plainData) throws SSLException {
        buffers.prepareForWrap(plainData);
        return doWrap();
    }

    /**
     * 加密明文数据（PooledByteBuffer 零拷贝版本）。
     * <p>直接从 PooledByteBuffer 底层数组写入内部缓冲区，消除中间 byte[] 分配。</p>
     *
     * @param plainData 待加密的明文（PooledByteBuffer）
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult wrap(PooledByteBuffer plainData) throws SSLException {
        buffers.prepareForWrap(plainData);
        return doWrap();
    }

    /**
     * 执行 SSLEngine.wrap() 并处理结果状态。
     */
    private SSLEngineResult doWrap() throws SSLException {
        SSLEngineResult result = engine.wrap(
                buffers.get(BufferType.OUT_PLAIN),
                buffers.get(BufferType.OUT_CIPHER));

        emitWrappedData(result);

        SSLEngineResult.Status status = result.getStatus();
        if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            throw new SSLException("BUFFER_UNDERFLOW during wrap");
        } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            buffers.grow(BufferType.OUT_CIPHER);
            ByteBuffer plainBuf = buffers.get(BufferType.OUT_PLAIN);
            // engine.wrap() 后 position/limit 已精确标记剩余数据，直接传递，无需 slice 拷贝
            if (plainBuf.hasRemaining()) {
                wrap(plainBuf);
            }
        } else if (status == SSLEngineResult.Status.CLOSED) {
            notifySessionClosed();
        }
        return result;
    }

    /**
     * 解密密文数据（ByteBuffer 版本）。
     * <p>将密文加载到入站缓冲区，调用 SSLEngine.unwrap()，
     * 并将产生的明文通过 {@link SSLFacade.SSLDataListener#onPlainData} 回调传递。</p>
     *
     * @param encryptedData 待解密的密文，null 表示处理缓存数据
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult unwrap(ByteBuffer encryptedData) throws SSLException {
        ByteBuffer allData = buffers.prependCached(encryptedData);
        buffers.prepareForUnwrap(allData);
        return doUnwrap();
    }

    /**
     * 解密密文数据（PooledByteBuffer 零拷贝版本）。
     * <p>缓存合并已由 {@link Buffers#prepareForUnwrap(PooledByteBuffer)} 内部处理，
     * 此处直接委托，无需中间 byte[] 转换。</p>
     *
     * @param encryptedData 待解密的密文（PooledByteBuffer）
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult unwrap(PooledByteBuffer encryptedData) throws SSLException {
        buffers.prepareForUnwrap(encryptedData);
        return doUnwrap();
    }

    /**
     * 执行 SSLEngine.unwrap() 并处理结果状态。
     * <p>调用前 IN_CIPHER 已加载数据并处于读模式（flip 后）。
     * unwrap 后通过 IN_CIPHER.position() 计算未处理数据偏移。</p>
     */
    private SSLEngineResult doUnwrap() throws SSLException {
        SSLEngineResult result = engine.unwrap(
                buffers.get(BufferType.IN_CIPHER),
                buffers.get(BufferType.IN_PLAIN));

        // IN_CIPHER 引用，engine.unwrap() 后 position/limit 已精确标记未处理数据
        ByteBuffer inCipher = buffers.get(BufferType.IN_CIPHER);

        emitPlainData(result);

        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                // 未处理数据缓存，等待下次到达更多数据
                if (inCipher.hasRemaining()) {
                    buffers.cache(inCipher);
                }
                break;
            case BUFFER_OVERFLOW:
                buffers.grow(BufferType.IN_PLAIN);
                // IN_CIPHER 中必有未处理数据（OVERFLOW 意味着数据未完全消费），直接传递
                unwrap(inCipher);
                break;
            case OK:
                if (inCipher.hasRemaining()) {
                    buffers.cache(inCipher);
                } else {
                    buffers.clearCache();
                }
                break;
            case CLOSED:
                break;
        }
        if (!buffers.isCacheEmpty()
                && result.getStatus() == SSLEngineResult.Status.OK
                && result.bytesConsumed() > 0) {
            result = unwrap(EMPTY_BUFFER);
        }
        return result;
    }

    /**
     * 关闭 SSL 连接。
     * <p>发送 close_notify 警报并关闭入站连接。</p>
     */
    void close() {
        engine.closeOutbound();
        try {
            wrap((ByteBuffer) null);
            engine.closeInbound();
        } catch (SSLException ignored) {
            // 关闭过程中的异常可忽略
        }
    }

    // ---- 内部方法 ----

    /**
     * 将加密后的密文数据通过回调传递。
     * <p>直接传递内部缓冲区引用，消除 copyToExternal 的中间分配和拷贝。
     * 回调结束后恢复缓冲区的 position/limit，保证内部状态不被破坏。</p>
     */
    private void emitWrappedData(SSLEngineResult result) {
        if (result.bytesProduced() > 0 && dataListener != null) {
            ByteBuffer buf = buffers.get(BufferType.OUT_CIPHER);
            int pos = buf.position();
            int lim = buf.limit();
            buf.flip();
            try {
                dataListener.onWrappedData(buf);
            } finally {
                buf.position(pos);
                buf.limit(lim);
            }
        }
    }

    /**
     * 将解密后的明文数据通过回调传递。
     * <p>直接传递内部缓冲区引用，消除 copyToExternal 的中间分配和拷贝。
     * 回调结束后恢复缓冲区的 position/limit，保证内部状态不被破坏。</p>
     */
    private void emitPlainData(SSLEngineResult result) {
        if (result.bytesProduced() > 0 && dataListener != null) {
            ByteBuffer buf = buffers.get(BufferType.IN_PLAIN);
            int pos = buf.position();
            int lim = buf.limit();
            buf.flip();
            try {
                dataListener.onPlainData(buf);
            } finally {
                buf.position(pos);
                buf.limit(lim);
            }
        }
    }

    /**
     * 通知会话已关闭。
     */
    private void notifySessionClosed() {
        if (sessionClosedCallback != null) {
            sessionClosedCallback.run();
        }
    }
}
