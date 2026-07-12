# SSL/TLS 加密通信

本页介绍如何在 Getty 中配置和使用 SSL/TLS 加密通信。

## 概述

Getty 的 SSL 功能通过 `SSLHandler` 实现，即插即用，添加到管道头部即可自动加解密。支持 AIO 和 NIO 双模式。

## SSL 配置

### SSLConfig

```java
SSLConfig sslConfig = new SSLConfig();

// 密钥库（必须）
sslConfig.setKeyFile("/path/to/keystore.jks");       // 密钥库文件路径
sslConfig.setKeystorePassword("keystorePassword");    // 密钥库密码
sslConfig.setKeyPassword("keyPassword");              // 密钥条目密码

// 信任库（可选，null 时信任所有证书）
sslConfig.setTrustFile("/path/to/truststore.jks");
sslConfig.setTrustPassword("trustPassword");

// 模式设置
sslConfig.setClientMode(false);              // false=服务端，true=客户端
sslConfig.setClientAuthRequired(false);      // 是否要求客户端证书（仅服务端有效）

// 协议版本（可选，默认 TLSv1.2）
sslConfig.setProtocolVersion("TLSv1.2");
```

### SSLConfig 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `protocolVersion` | String | TLSv1.2 | SSL 协议版本 |
| `clientMode` | boolean | false | true=客户端模式，false=服务端模式 |
| `keyFile` | String | — | 密钥库文件路径（JKS 格式） |
| `keystorePassword` | String | — | 密钥库访问密码 |
| `keyPassword` | String | — | 密钥条目密码 |
| `trustFile` | String | null | 信任库文件路径，null 时信任所有证书 |
| `trustPassword` | String | — | 信任库访问密码 |
| `clientAuthRequired` | boolean | false | 是否要求客户端证书认证（仅服务端） |

## 服务端 SSL

```java
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // SSL 配置（加到管道最前面）
    SSLConfig sslConfig = new SSLConfig();
    sslConfig.setKeyFile(getClass().getClassLoader().getResource("serverStore.jks").getPath());
    sslConfig.setKeyPassword("123456");
    sslConfig.setKeystorePassword("123456");
    sslConfig.setTrustFile(getClass().getClassLoader().getResource("serverStore.jks").getPath());
    sslConfig.setTrustPassword("123456");
    sslConfig.setClientMode(false);              // 服务端模式
    sslConfig.setClientAuthRequired(false);      // 不要求客户端证书
    
    // SSL Handler 加到管道头部
    pipeline.addFirst(new SSLHandler(sslConfig));
    
    // 其他处理器
    pipeline.addLast(new StringEncoder());
    pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
    pipeline.addLast(new StringDecoder());
    pipeline.addLast(new MyBusinessHandler());
});
server.start();
```

## 客户端 SSL

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 8888);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // SSL 配置
    SSLConfig sslConfig = new SSLConfig();
    sslConfig.setKeyFile(getClass().getClassLoader().getResource("clientStore.jks").getPath());
    sslConfig.setKeyPassword("123456");
    sslConfig.setKeystorePassword("123456");
    sslConfig.setTrustFile(getClass().getClassLoader().getResource("clientStore.jks").getPath());
    sslConfig.setTrustPassword("123456");
    sslConfig.setClientMode(true);               // 客户端模式
    
    // SSL Handler 加到管道头部
    pipeline.addFirst(new SSLHandler(sslConfig));
    
    // 其他处理器
    pipeline.addLast(new StringEncoder());
    pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
    pipeline.addLast(new StringDecoder());
    pipeline.addLast(new MyBusinessHandler());
});
client.start(connectHandler);
```

## SSL 握手监听

可以注册握手监听器，在 SSL 握手完成时收到回调：

```java
channel.setSslHandshakeListener(new IHandshakeListener() {
    @Override
    public void handshakeComplete() {
        System.out.println("SSL 握手完成");
    }

    @Override
    public void handshakeFailed(Exception e) {
        System.err.println("SSL 握手失败: " + e.getMessage());
    }
});
```

## 生成测试证书

使用 JDK 自带的 `keytool` 生成自签名证书：

```bash
# 生成服务端密钥库
keytool -genkey -alias server -keyalg RSA -keysize 2048 -validity 365 \
  -keystore serverStore.jks -storepass 123456 -keypass 123456 \
  -dname "CN=localhost, OU=Getty, O=Getty, L=City, ST=State, C=CN"

# 生成客户端密钥库
keytool -genkey -alias client -keyalg RSA -keysize 2048 -validity 365 \
  -keystore clientStore.jks -storepass 123456 -keypass 123456 \
  -dname "CN=localhost, OU=Getty, O=Getty, L=City, ST=State, C=CN"

# 导出服务端证书
keytool -export -alias server -file server.cer -keystore serverStore.jks -storepass 123456

# 将服务端证书导入客户端信任库
keytool -import -alias server -file server.cer -keystore clientStore.jks -storepass 123456 -noprompt

# 导出客户端证书
keytool -export -alias client -file client.cer -keystore clientStore.jks -storepass 123456

# 将客户端证书导入服务端信任库
keytool -import -alias client -file client.cer -keystore serverStore.jks -storepass 123456 -noprompt
```

## 注意事项

1. **SSLHandler 必须加到管道头部**：使用 `pipeline.addFirst()` 而非 `addLast()`
2. **clientMode 必须正确设置**：服务端 `false`，客户端 `true`
3. **双向认证**：设置 `clientAuthRequired(true)` 时，客户端必须提供证书
4. **性能影响**：SSL 加解密会消耗 CPU 资源，生产环境建议使用合适的密钥长度
5. **证书路径**：使用 `getClass().getClassLoader().getResource().getPath()` 获取 classpath 下的资源路径

## 下一步

- [高级特性：ChannelGroup 与流控](高级特性：ChannelGroup与流控) — 广播与流控
- [心跳与断线重连](心跳与断线重连) — 连接保活机制
- [AIO 编程指南](AIO编程指南) — AIO 模型详细说明
