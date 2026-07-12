# 配置参考：GettyConfig

`GettyConfig` 是 Getty 的统一配置类，客户端和服务端共用。本页列出所有可配置项及其说明。

## 基本配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `host` | String | null | 服务器地址。服务端为 null 时绑定所有网卡 |
| `port` | int | 0 | 监听/连接端口 |
| `client` | boolean | false | 是否为客户端模式（由 Starter 自动设置） |

## 缓冲区配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `readBufferSize` | int | 4096 (4KB) | 读缓冲区大小（字节） |
| `bufferWriterQueueSize` | int | 1048576 (1MB) | 写队列容量（字节） |
| `direct` | boolean | false | 是否使用直接内存（DirectByteBuffer） |

## 流控配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `flowControl` | boolean | false | 流控开关 |
| `highWaterMark` | int | = bufferWriterQueueSize | 高水位线：待写数据量达到此值时标记不可写 |
| `lowWaterMark` | int | = highWaterMark / 2 | 低水位线：待写数据量降至此值时恢复可写 |

## Socket 选项

通过 `setOption` 方法设置标准 Socket 选项：

```java
GettyConfig config = new GettyConfig();
config.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
config.setOption(StandardSocketOptions.TCP_NODELAY, true);
config.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);
config.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024);
config.setOption(StandardSocketOptions.SO_REUSEADDR, true);
```

支持的常用选项：

| 选项 | 说明 |
|------|------|
| `SO_SNDBUF` | 发送缓冲区大小 |
| `SO_RCVBUF` | 接收缓冲区大小 |
| `SO_KEEPALIVE` | 保持连接活跃（TCP 层心跳） |
| `SO_REUSEADDR` | 重用地址（服务端常用） |
| `TCP_NODELAY` | 禁用 Nagle 算法（降低延迟） |

## 使用示例

### 简单构造

```java
// 服务端 - 仅指定端口
AioServerStarter server = new AioServerStarter(8888);

// 服务端 - 指定地址和端口
AioServerStarter server = new AioServerStarter("0.0.0.0", 8888);

// 客户端 - 指定地址和端口
AioClientStarter client = new AioClientStarter("127.0.0.1", 8888);
```

### 自定义配置

```java
GettyConfig config = new GettyConfig();
config.setHost("127.0.0.1");
config.setPort(8888);

// 增大读缓冲区
config.setReadBufferSize(8 * 1024);

// 使用直接内存
config.setDirect(true);

// 开启流控
config.setFlowControl(true);
config.setHighWaterMark(512 * 1024);   // 512KB
config.setLowWaterMark(256 * 1024);    // 256KB

// Socket 选项
config.setOption(StandardSocketOptions.TCP_NODELAY, true);
config.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

// 使用配置创建 Starter
AioServerStarter server = new AioServerStarter(config);
```

### 客户端配置

```java
GettyConfig config = new GettyConfig();
config.setHost("192.168.1.100");
config.setPort(9999);
config.setReadBufferSize(16 * 1024);

AioClientStarter client = new AioClientStarter(config);
client.channelInitializer(channel -> {
    // 配置管道...
});
client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        System.out.println("连接成功");
    }
    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

## Starter 特有配置

### AIO 服务端

```java
AioServerStarter server = new AioServerStarter(8888);
server.writeThreadNum(4);    // 写线程数（默认 4，足以支撑万级连接）
server.bossThreadNum(4);     // Boss 线程数（最少 3 个）
```

### NIO 服务端

```java
NioServerStarter server = new NioServerStarter(8888);
server.socketMode(SocketMode.TCP);  // TCP 或 UDP
server.bossThreadNum(4);            // Boss 线程数
```

### NIO 客户端

```java
NioClientStarter client = new NioClientStarter("127.0.0.1", 8888);
client.socketMode(SocketMode.TCP);  // TCP 或 UDP
```

## 下一步

- [AIO 编程指南](AIO编程指南) — AIO 模型详细使用说明
- [NIO 编程指南](NIO编程指南) — NIO 模型详细使用说明
- [SSL/TLS 加密通信](SSL-TLS加密通信) — SSL 配置说明
