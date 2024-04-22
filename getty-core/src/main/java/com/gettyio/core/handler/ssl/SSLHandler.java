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

import com.gettyio.core.handler.ssl.facade.*;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * SslHandler.java
 *
 * @description:SSL 编解码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class SSLHandler extends ChannelAllBoundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SSLHandler.class);

    /**
     * 默认protocolVersion
     */
    private final String PROTOCOL = "TLSv1.2";
    /**
     * SSL上下文
     */
    private SSLContext sslContext;
    /**
     * 配置文件
     */
    private final SSLConfig config;

    private ISSLFacade ssl;

    public SSLHandler(SSLConfig sslConfig) {
        this.config = sslConfig;
    }

    @Override
    public void setChannelHandlerContext(ChannelHandlerContext ctx) {
        super.setChannelHandlerContext(ctx);
        ctx.channel().setSslHandler(this);
        init(this.config);
    }

    /**
     * 初始化
     *
     * @param config
     */
    private void init(SSLConfig config) {
        try {
            KeyManager[] keyManagers = null;
            if (config.getKeyFile() != null) {
                // 密钥库KeyStore
                KeyStore ks = KeyStore.getInstance("JKS");
                // 加载服务端的KeyStore，用于检查密钥库完整性的密码
                ks.load(new FileInputStream(config.getKeyFile()), config.getKeystorePassword().toCharArray());
                // 初始化密钥管理器
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, config.getKeyPassword().toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            //初始化签名证书管理器
            TrustManager[] trustManagers;
            if (config.getTrustFile() != null) {
                // 密钥库KeyStore
                KeyStore ts = KeyStore.getInstance("JKS");
                // 加载信任库
                ts.load(new FileInputStream(config.getTrustFile()), config.getTrustPassword().toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            } else {
                trustManagers = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
            }

            //初始化上下文
            sslContext = SSLContext.getInstance(config.getProtocolVersion() != null ? config.getProtocolVersion() : PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            createSSLFacade(new handshakeCompletedListener(), new SSLListener(), new sessionClosedListener());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createSSLFacade(IHandshakeCompletedListener handshakeCompletedListener, ISSLListener SSLListener, ISessionClosedListener sessionClosedListener) {
        ssl = new SSLFacade(sslContext, config.isClientMode(), config.isClientAuth(), new DefaultTaskHandler(),channelHandlerContext().channel().getByteBufferPool());
        ssl.setHandshakeCompletedListener(handshakeCompletedListener);
        ssl.setSSLListener(SSLListener);
        ssl.setCloseListener(sessionClosedListener);
    }

    /**
     * 开始握手
     */
    public void beginHandshake() {
        try {
            ssl.beginHandshake();
        } catch (IOException e) {
            logger.error("beginHandshake error", e);
        }
    }


    public boolean isHandshakeCompleted() {
        return ssl.isHandshakeCompleted();
    }


    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        byte[] bytes = (byte[]) obj;
        if (!ssl.isHandshakeCompleted() && obj != null) {
            //握手
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            try {
                ssl.decrypt(byteBuffer);
                byte[] b = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes, 0, b.length);
                ctx.channel().writeToChannel(b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                ssl.close();
            }
        } else if (bytes != null) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //SSL doUnWard
            ssl.encrypt(byteBuffer);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        byte[] bytes = (byte[]) obj;
        if (!ssl.isHandshakeCompleted() && obj != null) {
            //握手
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            try {
                ssl.decrypt(byteBuffer);
                byte[] b = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes, 0, b.length);
                ctx.channel().writeToChannel(b);
            } catch (Exception e) {
                ssl.close();
            }
        } else if (bytes != null) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //SSL doUnWard
            ssl.decrypt(byteBuffer);
        }
    }


    /**
     * 握手成功回调
     */
    class handshakeCompletedListener implements IHandshakeCompletedListener {
        @Override
        public void onComplete() {
            logger.info("ssl handshake completed");
            channelHandlerContext().channel().setHandShake(true);
            IHandshakeListener iHandshakeCompletedListener = channelHandlerContext().channel().getSslHandshakeListener();
            if (iHandshakeCompletedListener != null) {
                iHandshakeCompletedListener.onComplete();
            }
        }
    }

    /**
     * 握手关闭回调
     */
    class sessionClosedListener implements ISessionClosedListener {
        @Override
        public void onSessionClosed() {
            logger.info("ssl handshake failure");
            channelHandlerContext().channel().setHandShake(false);
            //当握手失败时，关闭当前客户端连接
            channelHandlerContext().channel().close();
            IHandshakeListener iHandshakeCompletedListener = channelHandlerContext().channel().getSslHandshakeListener();
            if (iHandshakeCompletedListener != null) {
                iHandshakeCompletedListener.onFail(new SSLException("ssl handshake failure"));
            }
        }
    }

    /**
     * SSL回调
     */
    class SSLListener implements ISSLListener {

        @Override
        public void onWrappedData(ByteBuffer wrappedBytes) {
            try {
                byte[] b = new byte[wrappedBytes.remaining()];
                wrappedBytes.get(b, 0, b.length);
                channelHandlerContext().channel().writeToChannel(b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        @Override
        public void onPlainData(ByteBuffer plainBytes) {
            //消息解码
            byte[] b = new byte[plainBytes.remaining()];
            plainBytes.get(b, 0, b.length);
            try {
                SSLHandler.super.channelRead(channelHandlerContext(), b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


}
