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

import com.gettyio.core.handler.ssl.sslfacade.*;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


/**
 * SslService.java
 *
 * @description:ssl服务
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class SslService {

    /**
     * 默认protocolVersion
     */
    private static String PROTOCOL = "TLSv1.2";
    /**
     * SSL上下文
     */
    private SSLContext sslContext;
    /**
     * 配置文件
     */
    private SslConfig config;

    private ISSLFacade ssl;

    public SslService(SslConfig config) {
        this.config = config;
        init(config);
    }

    public SslService(SslConfig config, String protocolVersion) {
        this.PROTOCOL = protocolVersion;
        this.config = config;
        init(config);
    }

    /**
     * 初始化
     *
     * @param config
     */
    private void init(SslConfig config) {
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
            sslContext = SSLContext.getInstance(PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void createSSLFacade(IHandshakeCompletedListener handshakeCompletedListener, ISSLListener SSLListener, ISessionClosedListener sessionClosedListener) {
        ssl = new SSLFacade(sslContext, config.isClientMode(), config.isClientAuth(), new DefaultTaskHandler());
        ssl.setHandshakeCompletedListener(handshakeCompletedListener);
        ssl.setSSLListener(SSLListener);
        ssl.setCloseListener(sessionClosedListener);
    }


    /**
     * 开始握手
     */
    public void beginHandshake(IHandshakeCompletedListener handshakeCompletedListener) {
        try {
            if (null != handshakeCompletedListener) {
                ssl.setHandshakeCompletedListener(handshakeCompletedListener);
            }
            ssl.beginHandshake();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void beginHandshake() {
        try {
            ssl.beginHandshake();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ISSLFacade getSsl() {
        return ssl;
    }
}
