# NIO 编程指南

本页详细介绍 Getty 中 NIO（非阻塞 I/O）模型的编程方式。

## NIO 模型概述

NIO 基于 Java NIO 的 `SocketChannel` + `Selector`，通过 EventLoop 轮询事件驱动。

**特点：**
- 读/写事件由 `NioEventLoop` 的 `Selector` 轮询驱动
- 读缓冲区在 EventLoop 级别复用，减少内存分配
- `SelectedSelector` 自动检测修复 Selector 空轮询 bug
- `selectedKeys` 采用 `toArray + clear` 模式避免迭代器分配
- 适合中等并发、需要精细控制的场景

## 服务端

### 基本用法

```java
NioServerStarter server = new NioServerStarter(8888);
server.socketMode(SocketMode.TCP)
      .channelInitializer(channel -> {
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
NioServerStarter server = new NioServerStarter(8888);
server.bossThreadNum(4);  // Boss 线程数（最少 3 个）
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

NioServerStarter server = new NioServerStarter(config);
server.socketMode(SocketMode.TCP);
server.channelInitializer(channel -> {
    // ...
});
server.start();
```

## 客户端

### 基本用法

```java
NioClientStarter client = new NioClientStarter("127.0.0.1", 8888);
client.socketMode(SocketMode.TCP)
      .channelInitializer(channel -> {
          channel.getChannelPipeline()
              .addLast(new StringEncoder())
              .addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER))
              .addLast(new StringDecoder())
              .addLast(new MyHandler());
      });

// 同步启动
client.start();

// 异步启动（推荐）
client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        channel.writeAndFlush("Hello\r\n");
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

## 线程模型

```
┌─────────────────────────────────────────────┐
│  Accept Thread (非 daemon)                     │
│  负责 accept 新连接                            │
│  保持 JVM 存活直到 shutdown                     │
├─────────────────────────────────────────────┤
│  NioEventLoop 池 (Worker 线程)                  │
│  ┌─────────────────────────────────────┐     │
│  │ EventLoop-1: Selector + 通道组 1      │     │
│  │ EventLoop-2: Selector + 通道组 2      │     │
│  │ EventLoop-3: Selector + 通道组 3      │     │
│  │ ...                                    │     │
│  └─────────────────────────────────────┘     │
│  新连接轮询分配到各 EventLoop                    │
│  每个 EventLoop 独立处理读/写事件                │
└─────────────────────────────────────────────┘
```

## NIO 与 AIO 对比

| 特性 | AIO 模型 | NIO 模型 |
|------|---------|---------|
| 底层通道 | `AsynchronousSocketChannel` | `SocketChannel` + `Selector` |
| 读驱动 | OS 回调 `CompletionHandler` | `EventLoop` select 轮询 |
| 写驱动 | 共享写线程组 `AioWriteThreadGroup` | `EventLoop` OP_WRITE |
| 适用场景 | 高并发长连接（万级+） | 中等并发、精细控制 |
| Starter | `AioServerStarter` / `AioClientStarter` | `NioServerStarter` / `NioClientStarter` |

**切换方式：** 只需替换 Starter 类名，Pipeline 配置和业务处理器代码完全相同。

## 完整示例

参考 `getty-example/string` 模块中的 `NioServer` 和 `NioClient`：

```java
// NIO TCP 服务端
public class NioServer {
    public static void main(String[] args) {
        try {
            NioServerStarter server = new NioServerStarter(8888);
            server.socketMode(SocketMode.TCP).channelInitializer(channel -> {
                ChannelPipeline pipeline = channel.getChannelPipeline();
                pipeline.addLast(new StringEncoder());
                pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
                pipeline.addLast(new StringDecoder());
                pipeline.addLast(new SimpleHandler());
            }).start();
            System.out.println("NIO TCP 服务器已启动，端口: 8888");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 下一步

- [AIO 编程指南](AIO编程指南) — AIO 模型对比说明
- [UDP 通信指南](UDP通信指南) — UDP 模式的使用
- [SSL/TLS 加密通信](SSL-TLS加密通信) — NIO 场景下的 SSL 配置
