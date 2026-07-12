# 核心概念：Channel 与 Pipeline

本页详细介绍 Getty 中最核心的两个概念：Channel（通道）和 Pipeline（责任链管道）。

## Channel（通道）

### 通道类型

Getty 提供三种通道实现，统一继承自 `AbstractSocketChannel`：

| 通道类 | I/O 模型 | 协议 | 说明 |
|--------|---------|------|------|
| `AioChannel` | AIO (NIO2) | TCP | 基于 `AsynchronousSocketChannel`，OS 异步回调 |
| `NioChannel` | NIO | TCP | 基于 `SocketChannel` + `Selector`，EventLoop 驱动 |
| `UdpChannel` | NIO | UDP | 基于 `DatagramChannel` + `Selector` |

### 通道核心 API

```java
// 写出数据（经管道编码后发出）
channel.writeAndFlush(Object msg);

// 分步写出：先编码追加到缓冲区，再刷新发出
channel.write(Object msg);
channel.flush();

// 关闭通道
channel.close();

// 获取通道唯一标识
String id = channel.getChannelId();

// 通道是否已关闭
boolean closed = channel.isInvalid();

// 设置长连接/短连接模式
channel.setKeepAlive(true);  // 默认 true
```

### 通道属性

每个通道都附带一个线程安全的属性 Map，可以绑定任意业务数据：

```java
// 设置属性
channel.setChannelAttribute("userId", "user-001");
channel.setChannelAttribute("session", sessionObj);

// 获取属性
String userId = (String) channel.getChannelAttribute("userId");

// 删除属性
channel.removeChannelAttribute("userId");

// 获取全部属性（ConcurrentSafeMap）
ConcurrentSafeMap<String, Object> attrs = channel.getChannelAttribute();
```

### 通道关闭监听器

可以注册监听器，在通道关闭时收到回调：

```java
// 添加关闭监听器
channel.addChannelFutureListener(ch -> {
    System.out.println("通道关闭: " + ch.getChannelId());
    // 执行清理逻辑
});

// 设置关闭监听器（替换所有已有监听器）
channel.setChannelFutureListener(ch -> {
    // ...
});

// 移除监听器
channel.removeChannelFutureListener(listener);
```

## Pipeline（责任链管道）

### 基本概念

Pipeline 是 Getty 的核心设计，采用双向链表结构，将多个 Handler 串联起来处理入站和出站事件。

```
入站方向（head → tail）:
  head → [Decoder] → [SSLHandler] → [BusinessHandler] → tail

出站方向（tail → head）:
  tail → [BusinessHandler] → [SSLHandler] → [Encoder] → head → writeToSocket
```

### 添加 Handler

通过 `ChannelInitializer` 在通道建立时配置 Pipeline：

```java
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // 添加编码器（出站）
    pipeline.addLast(new StringEncoder());
    
    // 添加分帧器（入站）
    pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
    
    // 添加解码器（入站）
    pipeline.addLast(new StringDecoder());
    
    // 添加业务处理器（入站）
    pipeline.addLast(new MyBusinessHandler());
});
```

### addFirst 与 addLast

- `addLast(handler)` — 添加到链表尾部（最常用）
- `addFirst(handler)` — 添加到链表头部（通常用于 SSL 等底层处理器）

```java
// SSL 通常加到最前面
pipeline.addFirst(new SSLHandler(sslConfig));

// 编解码器和业务处理器按顺序添加到尾部
pipeline.addLast(new StringEncoder());
pipeline.addLast(new StringDecoder());
pipeline.addLast(new MyHandler());
```

### 事件传播

- **入站事件**（读、连接建立、连接关闭、异常、心跳）从 head 向 tail 传播
- **出站事件**（写）从 tail 向 head 传播
- 每个 Handler 处理完后调用 `ctx.fireChannelProcess()` 传递给下一个

```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        // 处理数据
        System.out.println("收到: " + in);
        // 传递给下一个入站处理器
        super.channelRead(ctx, in);
    }
}
```

## Handler（处理器）

### 处理器基类

| 基类 | 说明 | 使用场景 |
|------|------|---------|
| `ChannelHandlerAdapter` | 通用基类，所有事件默认透传 | 需要同时处理入站和出站 |
| `ChannelInboundHandlerAdapter` | 入站处理器基类 | 只关心入站事件 |
| `ChannelAllBoundHandlerAdapter` | 双向处理器基类 | 语义上区分"双向处理器" |
| `SimpleChannelInboundHandler<T>` | 泛型消息处理器 | **推荐业务层使用**，自动类型转换 |

### SimpleChannelInboundHandler

最常用的业务处理器基类，自动将消息转换为指定类型：

```java
public class MyHandler extends SimpleChannelInboundHandler<String> {
    
    // 连接建立
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("连接建立");
    }
    
    // 收到消息（已自动转为 String 类型）
    @Override
    public void channelRead0(AbstractSocketChannel channel, String msg) {
        System.out.println("收到: " + msg);
        channel.writeAndFlush("echo: " + msg + "\r\n");
    }
    
    // 连接关闭
    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭");
    }
    
    // 异常处理
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("异常: " + cause.getMessage());
        ctx.channel().close();
    }
    
    // 心跳空闲事件
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, IdleState evt) {
        if (evt == IdleState.READER_IDLE) {
            System.out.println("读空闲，发送心跳");
        }
    }
}
```

### ChannelHandlerContext

每个 Handler 被添加到 Pipeline 时都会绑定一个 `ChannelHandlerContext`，通过它可以：

```java
// 获取当前通道
AbstractSocketChannel ch = ctx.channel();

// 将事件传递给下一个处理器
ctx.fireChannelProcess(channelState, data);
```

## 编写自定义 Handler

### 入站处理器示例

```java
public class MessageCounter extends ChannelInboundHandlerAdapter {
    
    private int count = 0;
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) {
        count++;
        System.out.println("已收到 " + count + " 条消息");
        // 继续传递给下一个处理器
        ctx.fireChannelProcess(ChannelState.CHANNEL_READ, in);
    }
}
```

### 出站编码器示例

```java
public class JsonEncoder extends ChannelHandlerAdapter {
    
    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof MyMessage) {
            String json = toJson((MyMessage) obj);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            // 获取内存池缓冲区
            PooledByteBuffer buf = ctx.channel().getByteBufferPool().acquire(bytes.length);
            buf.writeBytes(bytes);
            // 传递给下一个出站处理器
            ctx.fireChannelProcess(ChannelState.CHANNEL_WRITE, buf);
        } else {
            ctx.fireChannelProcess(ChannelState.CHANNEL_WRITE, obj);
        }
    }
}
```

## 下一步

- [配置参考：GettyConfig](配置参考：GettyConfig) — 了解所有可配置项
- [AIO 编程指南](AIO编程指南) — AIO 模型的详细使用说明
- [NIO 编程指南](NIO编程指南) — NIO 模型的详细使用说明
