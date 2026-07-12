# AIO 编程指南

本页详细介绍 Getty 中 AIO（异步 I/O）模型的编程方式。

## AIO 模型概述

AIO（Asynchronous I/O）基于 Java NIO2 的 `AsynchronousSocketChannel`，由操作系统内核完成 I/O 操作后通过回调通知应用程序。

**特点：**
- 读操作由 OS 回调 `CompletionHandler` 驱动
- 写操作由共享写线程组 `AioWriteThreadGroup` 管理
- 适合高并发长连接场景（万级+连接）
- 无数据时写线程 `park` 阻塞，零 CPU 空转

## 服务端

### 基本用法

```java
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    pipeline.addLast(new StringEncoder());
    pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
    pipeline.addLast(new StringDecoder());
    pipeline.addLast(new MyHandler());
});
server.start();
```

### 自定义线程参数

```java
AioServerStarter server = new AioServerStarter(8888);
server.writeThreadNum(8);    // 写线程数（默认 4）
server.bossThreadNum(4);     // Boss 线程数（最少 3 个）
server.channelInitializer(channel -> {
    // ...
});
server.start();
```

### 使用 GettyConfig

```java
GettyConfig config = new GettyConfig();
config.setHost("0.0.0.0");
config.setPort(8888);
config.setReadBufferSize(8 * 1024);
config.setDirect(true);  // 使用直接内存

AioServerStarter server = new AioServerStarter(config);
server.channelInitializer(channel -> {
    // ...
});
server.start();
```

## 客户端

### 基本用法

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 8888);
client.channelInitializer(channel -> {
    channel.getChannelPipeline()
        .addLast(new StringEncoder())
        .addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER))
        .addLast(new StringDecoder())
        .addLast(new MyHandler());
});

// 同步启动（阻塞等待连接）
client.start();

// 异步启动（推荐，带连接回调）
client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        // 连接建立成功，可以发送数据
        channel.writeAndFlush("Hello\r\n");
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

### 使用 GettyConfig

```java
GettyConfig config = new GettyConfig();
config.setHost("127.0.0.1");
config.setPort(8888);

AioClientStarter client = new AioClientStarter(config);
client.channelInitializer(channel -> {
    // ...
});
client.start(connectHandler);
```

## 线程模型

```
┌─────────────────────────────────────────────┐
│  AsynchronousChannelGroup                     │
│  ┌─────────────────────────────────────┐     │
│  │ Boss 线程池（默认 3 个线程）           │     │
│  │ 负责 accept 新连接                    │     │
│  └─────────────────────────────────────┘     │
├─────────────────────────────────────────────┤
│  ReadCompletionHandler                        │
│  每个 Channel 的读操作由 OS 回调完成           │
├─────────────────────────────────────────────┤
│  AioWriteThreadGroup                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐│
│  │Write-1 │ │Write-2 │ │Write-3 │ │Write-4 ││
│  └────────┘ └────────┘ └────────┘ └────────┘│
│  4 个写线程共享管理所有 Channel 的写出          │
│  channel.write() 异步提交，OS 并行写出          │
└─────────────────────────────────────────────┘
```

## 连接管理

### 通道属性

```java
// 在处理器中绑定业务属性
@Override
public void channelAdded(ChannelHandlerContext ctx) {
    AbstractSocketChannel ch = ctx.channel();
    ch.setChannelAttribute("loginTime", System.currentTimeMillis());
}
```

### 通道组（广播）

```java
// 创建通道组
DefaultChannelGroup group = new DefaultChannelGroup("all-channels");

// 新连接加入组
@Override
public void channelAdded(ChannelHandlerContext ctx) {
    group.add(ctx.channel());
}

// 广播消息
group.writeToAll("广播消息\r\n");
```

### 短连接模式

```java
// 设置为短连接，写完后自动关闭
channel.setKeepAlive(false);
channel.writeAndFlush("response\r\n");
// 写出完成后通道自动关闭
```

## 完整示例

参考 `getty-example/string` 模块中的 `AioServer` 和 `AioClient`：

```java
// 服务端完整示例
public class AioServer {
    public static void main(String[] args) throws Exception {
        AioServerStarter server = new AioServerStarter(8888);
        server.channelInitializer(channel -> {
            ChannelPipeline pipeline = channel.getChannelPipeline();
            pipeline.addLast(new StringEncoder());
            pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
            pipeline.addLast(new StringDecoder());
            pipeline.addLast(new SimpleHandler());
        });
        server.start();
        System.out.println("AIO 服务器已启动，端口: 8888");
    }
}
```

## 下一步

- [NIO 编程指南](NIO编程指南) — NIO 模型对比说明
- [SSL/TLS 加密通信](SSL-TLS加密通信) — AIO 场景下的 SSL 配置
- [心跳与断线重连](心跳与断线重连) — 连接保活机制
