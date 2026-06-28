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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.ssl.facade.SSLFacade;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * SSL/TLS 编解码处理器。
 * <p>
 * 作为管道中的双向处理器，负责：
 * <ul>
 *   <li>出站方向：将应用数据通过 SSLEngine 加密后发送到网络</li>
 *   <li>入站方向：将网络密文通过 SSLEngine 解密为应用数据</li>
 *   <li>握手阶段：自动处理 TLS 握手的来回交互，握手完成后切换到数据传输模式</li>
 * </ul>
 * </p>
 *
 * <p><b>注意：</b>此处理器必须置于管道链的第一个位置（最靠近网络层），
 * 以确保所有出站数据先加密、所有入站数据先解密。</p>
 */
public class SSLHandler extends ChannelAllBoundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SSLHandler.class);

    /** 默认 TLS 协议版本 */
    private static final String DEFAULT_PROTOCOL = "TLSv1.2";

    /** SSL 配置 */
    private final SSLConfig config;

    /** SSL facade，封装了 SSLEngine 的所有操作 */
    private SSLFacade ssl;

    public SSLHandler(SSLConfig config) {
        this.config = config;
    }

    /**
     * 处理器绑定到通道时初始化 SSL 引擎。
     * <p>将自身注册到通道的 SSL 处理器引用，并立即初始化 SSLContext。</p>
     */
    @Override
    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        super.setChannelHandlerContext(ctx);
        ctx.channel().setSslHandler(this);
        initSSL();
    }

    /**
     * 发起 SSL 握手。
     * <p>握手过程中的数据交互由 {@link #channelRead} 和 {@link #channelWrite} 自动处理。</p>
     */
    public void beginHandshake() {
        try {
            ssl.beginHandshake();
        } catch (Exception e) {
            logger.error("SSL handshake initiation failed", e);
        }
    }

    /**
     * 查询握手是否已完成。
     */
    public boolean isHandshakeCompleted() {
        return ssl.isHandshakeCompleted();
    }

    // ---- 出站：加密应用数据 ----

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) obj;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        if (!ssl.isHandshakeCompleted()) {
            processHandshake(bytes);
        } else {
            ssl.encrypt(byteBuffer);
        }
    }

    // ---- 入站：解密网络数据 ----

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) obj;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        if (!ssl.isHandshakeCompleted()) {
            processHandshake(bytes);
        } else {
            ssl.decrypt(byteBuffer);
        }
    }

    // ---- 握手数据处理（读写共用） ----

    /**
     * 处理握手阶段的 TLS 数据包。
     * <p>解密收到的握手消息，并将产生的握手响应数据写回通道。</p>
     *
     * @param tlsData TLS 协议数据
     */
    private void processHandshake(byte[] tlsData) {
        ByteBuffer buffer = ByteBuffer.wrap(tlsData);
        try {
            ssl.decrypt(buffer);
            // 握手响应数据已通过 onWrappedData 回调 → emitToChannel 发送，无需额外处理
        } catch (Exception e) {
            logger.error("SSL handshake data processing failed", e);
            ssl.close();
        }
    }

    // ---- SSL 初始化 ----

    /**
     * 根据配置初始化 SSLContext 和 SSLFacade。
     */
    private void initSSL() {
        try {
            KeyManager[] keyManagers = null;
            if (config.getKeyFile() != null) {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(new FileInputStream(config.getKeyFile()), config.getKeystorePassword().toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, config.getKeyPassword().toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            TrustManager[] trustManagers;
            if (config.getTrustFile() != null) {
                KeyStore ts = KeyStore.getInstance("JKS");
                ts.load(new FileInputStream(config.getTrustFile()), config.getTrustPassword().toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            } else {
                // 未指定信任库时，信任所有证书（仅用于开发/测试环境）
                trustManagers = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
            }

            String protocol = config.getProtocolVersion() != null ? config.getProtocolVersion() : DEFAULT_PROTOCOL;
            SSLContext sslContext = SSLContext.getInstance(protocol);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

            ssl = new SSLFacade(sslContext, config.isClientMode(), config.isClientAuthRequired());
            ssl.setHandshakeCompletedCallback(this::onHandshakeCompleted);
            ssl.setSessionClosedCallback(this::onSessionClosed);
            ssl.setDataListener(new SSLFacade.SSLDataListener() {
                @Override
                public void onWrappedData(ByteBuffer wrappedBytes) {
                    emitToChannel(wrappedBytes);
                }

                @Override
                public void onPlainData(ByteBuffer plainBytes) {
                    emitToUpstream(plainBytes);
                }
            });
        } catch (Exception e) {
            logger.error("SSL context initialization failed", e);
        }
    }

    // ---- 回调方法 ----

    /** SSL 握手成功：通知通道并触发用户注册的握手监听器 */
    private void onHandshakeCompleted() {
        logger.info("SSL handshake completed");
        channelHandlerContext().channel().setHandShake(true);
        IHandshakeListener listener = channelHandlerContext().channel().getSslHandshakeListener();
        if (listener != null) {
            listener.onComplete();
        }
    }

    /** SSL 会话关闭（握手失败）：通知通道并触发用户注册的握手监听器 */
    private void onSessionClosed() {
        logger.warn("SSL session closed (handshake failure)");
        channelHandlerContext().channel().setHandShake(false);
        channelHandlerContext().channel().close();
        IHandshakeListener listener = channelHandlerContext().channel().getSslHandshakeListener();
        if (listener != null) {
            listener.onFail(new SSLException("SSL handshake failure"));
        }
    }

    /**
     * 将加密数据写入底层通道。
     * <p>握手阶段自动 flush 以确保握手数据立即发出；应用数据阶段仅写入不 flush，
     * 由用户显式调用 flush 或 writeAndFlush 触发实际发送。</p>
     */
    private void emitToChannel(ByteBuffer wrappedBytes) {
        try {
            int len = wrappedBytes.remaining();
            if (len == 0) return;
            PooledByteBuffer buf = channelHandlerContext().channel().getByteBufferPool().acquire(len);
            buf.writeBytes(wrappedBytes);
            channelHandlerContext().channel().writeToSocket(buf);
            // 握手未完成时必须立即 flush，否则握手数据停留在 BufferWriter 队列中不会发出
            if (!ssl.isHandshakeCompleted()) {
                channelHandlerContext().channel().flush();
            }
        } catch (Exception e) {
            logger.error("Failed to write SSL wrapped data to channel", e);
        }
    }

    /** 将解密后的明文数据传播到管道链上游（使用 PooledByteBuffer 零拷贝传递） */
    private void emitToUpstream(ByteBuffer plainBytes) {
        try {
            int len = plainBytes.remaining();
            if (len == 0) return;
            // 单次拷贝：直接从 ByteBuffer 写入池化缓冲区
            PooledByteBuffer buf = channelHandlerContext().channel().getByteBufferPool().acquire(len);
            buf.writeBytes(plainBytes);
            super.channelRead(channelHandlerContext(), buf);
        } catch (Exception e) {
            logger.error("Failed to propagate decrypted data upstream", e);
        }
    }
}
