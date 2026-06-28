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
     * 加密明文数据。
     * <p>将明文加载到出站缓冲区，调用 SSLEngine.wrap()，
     * 并将产生的密文通过 {@link SSLFacade.SSLDataListener#onWrappedData} 回调传递。</p>
     *
     * @param plainData 待加密的明文，null 表示仅执行握手 wrap
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult wrap(ByteBuffer plainData) throws SSLException {
        buffers.prepareForWrap(plainData);
        SSLEngineResult result = engine.wrap(
                buffers.get(BufferType.OUT_PLAIN),
                buffers.get(BufferType.OUT_CIPHER));

        emitWrappedData(result);

        SSLEngineResult.Status status = result.getStatus();
        if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            throw new SSLException("BUFFER_UNDERFLOW during wrap");
        } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            buffers.grow(BufferType.OUT_CIPHER);
            if (plainData != null && plainData.hasRemaining()) {
                plainData.position(result.bytesConsumed());
                ByteBuffer remaining = BufferUtils.slice(plainData);
                wrap(remaining);
            }
        } else if (status == SSLEngineResult.Status.CLOSED) {
            notifySessionClosed();
        }
        return result;
    }

    /**
     * 解密密文数据。
     * <p>将密文加载到入站缓冲区，调用 SSLEngine.unwrap()，
     * 并将产生的明文通过 {@link SSLFacade.SSLDataListener#onPlainData} 回调传递。</p>
     *
     * @param encryptedData 待解密的密文，null 表示处理缓存数据
     * @return SSLEngine 的操作结果
     */
    SSLEngineResult unwrap(ByteBuffer encryptedData) throws SSLException {
        ByteBuffer allData = buffers.prependCached(encryptedData);
        buffers.prepareForUnwrap(allData);
        SSLEngineResult result = engine.unwrap(
                buffers.get(BufferType.IN_CIPHER),
                buffers.get(BufferType.IN_PLAIN));

        allData.position(result.bytesConsumed());
        ByteBuffer unprocessed = BufferUtils.slice(allData);

        emitPlainData(result);

        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                buffers.cache(unprocessed);
                break;
            case BUFFER_OVERFLOW:
                buffers.grow(BufferType.IN_PLAIN);
                if (unprocessed == null) {
                    throw new SSLException("BUFFER_OVERFLOW but all data consumed");
                }
                unwrap(unprocessed);
                break;
            case OK:
                if (unprocessed == null) {
                    buffers.clearCache();
                } else {
                    buffers.cache(unprocessed);
                }
                break;
            case CLOSED:
                break;
        }
        if (!buffers.isCacheEmpty()
                && result.getStatus() == SSLEngineResult.Status.OK
                && result.bytesConsumed() > 0) {
            result = unwrap(ByteBuffer.allocate(0));
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
            wrap(null);
            engine.closeInbound();
        } catch (SSLException ignored) {
            // 关闭过程中的异常可忽略
        }
    }

    // ---- 内部方法 ----

    /**
     * 将加密后的密文数据通过回调传递。
     */
    private void emitWrappedData(SSLEngineResult result) {
        if (result.bytesProduced() > 0 && dataListener != null) {
            dataListener.onWrappedData(copyToExternal(buffers.get(BufferType.OUT_CIPHER)));
        }
    }

    /**
     * 将解密后的明文数据通过回调传递。
     */
    private void emitPlainData(SSLEngineResult result) {
        if (result.bytesProduced() > 0 && dataListener != null) {
            dataListener.onPlainData(copyToExternal(buffers.get(BufferType.IN_PLAIN)));
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

    /**
     * 将内部缓冲区的数据复制到新的外部 ByteBuffer。
     * <p>创建独立副本供回调接收方安全使用，避免内部缓冲区被意外修改。</p>
     *
     * @param internalBuffer 内部缓冲区（flip 后 position=0, limit=数据长度）
     * @return 包含相同数据的新 ByteBuffer
     */
    private static ByteBuffer copyToExternal(ByteBuffer internalBuffer) {
        ByteBuffer external = ByteBuffer.allocate(internalBuffer.position());
        internalBuffer.flip();
        external.put(internalBuffer);
        external.flip();
        return external;
    }
}
