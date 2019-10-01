/**
 * 包名：org.getty.core.handler.ssl
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.ssl;

/**
 * 类名：SSLConfig.java
 * 描述：ssl配置
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class SslConfig {
    /**
     * 配置引擎在握手时使用客户端（或服务器）模式
     */
    private boolean clientMode;

    //keystore路径
    private String keyFile;
    //keystore密码
    private String keystorePassword;
    //创建管理jks密钥库的x509密钥管理器，用来管理密钥，需要key的密码，通常是keystore密码，这个密码也可以为null
    private String keyPassword;

    //签名证书路径，通常签名证书直接使用jks，也就是上面的keystore文件，并非cer文件
    private String trustFile;
    //签名证书密码
    private String trustPassword;

    private boolean clientAuth = ClientAuth.NONE;

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
