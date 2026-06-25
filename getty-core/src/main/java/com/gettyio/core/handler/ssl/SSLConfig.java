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
 * SSL/TLS 配置。
 * <p>
 * 封装 SSL 握手和加密通信所需的全部配置参数，包括密钥库、信任库、
 * 协议版本、客户端/服务器模式以及客户端认证开关。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * SSLConfig config = new SSLConfig();
 * config.setKeyFile("/path/to/keystore.jks");
 * config.setKeyPassword("password");
 * config.setKeystorePassword("password");
 * config.setClientMode(false);
 * config.setClientAuthRequired(true);
 * }</pre>
 */
public class SSLConfig {

    /** SSL 协议版本，null 时使用默认值 TLSv1.2 */
    private String protocolVersion;

    /** 是否以客户端模式运行（true=客户端，false=服务器） */
    private boolean clientMode;

    /** 密钥库文件路径（JKS 格式） */
    private String keyFile;

    /** 密钥库访问密码 */
    private String keystorePassword;

    /** 密钥条目密码（通常与密钥库密码相同） */
    private String keyPassword;

    /** 信任库文件路径（JKS 格式），null 时信任所有证书 */
    private String trustFile;

    /** 信任库访问密码 */
    private String trustPassword;

    /** 是否要求客户端证书认证（仅服务器模式有效） */
    private boolean clientAuthRequired;

    // ---- 访问方法 ----

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public boolean isClientMode() {
        return clientMode;
    }

    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
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

    public boolean isClientAuthRequired() {
        return clientAuthRequired;
    }

    public void setClientAuthRequired(boolean clientAuthRequired) {
        this.clientAuthRequired = clientAuthRequired;
    }
}
