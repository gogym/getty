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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * SSL 门面，封装 {@link SSLEngine} 的加密、解密和握手操作。
 * <p>
 * 内部委托 {@link Worker} 执行 wrap/unwrap 操作，委托 {@link Handshaker} 管理握手流程。
 * 通过回调接口 {@link SSLDataListener} 将加密后的密文和解密后的明文传递给调用方。
 * </p>
 *
 * <p><b>线程安全：</b>此类的实例不是线程安全的，应确保在同一线程中调用所有方法。</p>
 */
public class SSLFacade {

    /**
     * SSL 数据监听器。
     * <p>接收 SSL 引擎产生的加密数据（出站）和解密数据（入站）。</p>
     */
    public interface SSLDataListener {
        /**
         * 收到加密后的密文数据，应将其发送到网络通道。
         *
         * @param wrappedBytes 加密后的数据（position=0, limit=数据长度）
         */
        void onWrappedData(ByteBuffer wrappedBytes);

        /**
         * 收到解密后的明文数据，应将其传播到管道链上游。
         *
         * @param plainBytes 解密后的数据（position=0, limit=数据长度）
         */
        void onPlainData(ByteBuffer plainBytes);
    }

    private final Handshaker handshaker;
    private final Worker worker;
    private Runnable handshakeCompletedCallback;

    /**
     * 创建 SSL 门面实例。
     *
     * @param context          SSL 上下文（已初始化密钥和信任管理器）
     * @param clientMode       true=客户端模式，false=服务器模式
     * @param clientAuthRequired true=要求客户端证书认证（仅服务器模式有效）
     */
    public SSLFacade(SSLContext context, boolean clientMode, boolean clientAuthRequired) {
        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(clientMode);
        engine.setNeedClientAuth(clientAuthRequired);
        engine.setEnabledProtocols(new String[]{context.getProtocol()});

        Buffers buffers = new Buffers(engine.getSession());
        worker = new Worker(engine, buffers);
        handshaker = new Handshaker(worker, this::onHandshakeFinished);
    }

    /**
     * 设置握手完成回调。
     */
    public void setHandshakeCompletedCallback(Runnable callback) {
        this.handshakeCompletedCallback = callback;
    }

    /**
     * 设置会话关闭回调（握手失败或 TLS 连接关闭时触发）。
     */
    public void setSessionClosedCallback(Runnable callback) {
        worker.setSessionClosedCallback(callback);
    }

    /**
     * 设置数据监听器，接收加密和解密后的数据。
     */
    public void setDataListener(SSLDataListener listener) {
        worker.setDataListener(listener);
    }

    /**
     * 发起 SSL 握手。
     */
    public void beginHandshake() throws SSLException {
        handshaker.begin();
    }

    /**
     * 查询握手是否已完成。
     */
    public boolean isHandshakeCompleted() {
        return handshaker.isFinished();
    }

    /**
     * 加密明文数据。
     * <p>加密后的密文通过 {@link SSLDataListener#onWrappedData} 回调传递。</p>
     *
     * @param plainData 待加密的明文
     */
    public void encrypt(ByteBuffer plainData) throws SSLException {
        worker.wrap(plainData);
    }

    /**
     * 解密密文数据。
     * <p>解密后的明文通过 {@link SSLDataListener#onPlainData} 回调传递。</p>
     *
     * @param encryptedData 待解密的密文
     */
    public void decrypt(ByteBuffer encryptedData) throws SSLException {
        handshaker.handleDecrypt(worker.unwrap(encryptedData));
    }

    /**
     * 关闭 SSL 连接，发送 close_notify 警报。
     */
    public void close() {
        worker.close();
    }

    /** 握手完成时由 Handshaker 触发 */
    private void onHandshakeFinished() {
        if (handshakeCompletedCallback != null) {
            handshakeCompletedCallback.run();
            handshakeCompletedCallback = null;
        }
    }
}
