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
 * SslConfig.java
 *
 * @description:ssl配置
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class SSLConfig {

    /**
     * 协议版本
     */
    private String protocolVersion;

    /**
     * 配置引擎在握手时使用客户端（或服务器）模式
     */
    private boolean clientMode;
    /**
     * keystore路径
     */
    private String keyFile;
    /**
     * keystore密码
     */
    private String keystorePassword;
    /**
     * 创建管理jks密钥库的x509密钥管理器，用来管理密钥，需要key的密码，通常是keystore密码，这个密码也可以为null
     */
    private String keyPassword;
    /**
     * 签名证书路径，通常签名证书直接使用jks，也就是上面的keystore文件，并非cer文件
     */
    private String trustFile;
    /**
     * 签名证书密码
     */
    private String trustPassword;

    /**
     * 客户端鉴权模式
     */
    private boolean clientAuth = ClientAuth.NONE;

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getTrustFile() {
        return trustFile;
    }

    public void setTrustFile(String trustFile) {
        this.trustFile = trustFile;
    }

    public String getTrustPassword() {
        return trustPassword;
    }

    public void setTrustPassword(String trustPassword) {
        this.trustPassword = trustPassword;
    }

    public boolean isClientMode() {
        return clientMode;
    }

    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }

    public boolean isClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(boolean clientAuth) {
        this.clientAuth = clientAuth;
    }
}
