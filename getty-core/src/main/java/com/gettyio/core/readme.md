# Getty Core 架构

## 一、模块总览

Getty Core 是框架的核心引擎，提供从网络 I/O、内存管理到事件分发的完整通信基础设施。设计思想借鉴 Netty，同时针对 Java AIO/NIO 双模型做了深度优化，追求**极致性能**与**高可扩展性**。

```
com.gettyio.core/
├── buffer/              # 缓冲区与内存池
│   ├── pool/            #   三级内存池（ThreadCache → Arena → Chunk）
│   ├── AutoByteBuffer   #   自动扩容缓冲区
│   └── BufferWriter     #   无锁写出队列
├── channel/             # 通道与启动器
│   ├── config/          #   统一配置 GettyConfig
│   ├── group/           #   通道组（广播）
│   ├── internal/        #   AIO 读写回调
│   ├── loop/            #   事件循环（NioEventLoop / AioWriteThread）
│   └── starter/         #   启动器（AIO/NIO 客户端/服务端）
├── handler/             # 内置处理器
│   ├── codec/           #   编解码器基类
│   └── ssl/             #   SSL/TLS 加密解密
├── pipeline/            # 责任链管道
├── logging/             # 日志抽象层
├── constant/            # 常量（Banner、版本、空闲状态）
└── util/                # 工具集
    ├── list/            #   高性能列表
    ├── queue/           #   无锁队列（MPSC）
    ├── thread/          #   线程池与锁工具
    └── timer/           #   时间轮（HashedWheelTimer）
```

---

## 二、整体架构

### 2.1 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                     用户业务代码                               │
│         继承 SimpleChannelInboundHandler 实现业务逻辑          │
├──────────────────────────────────────────────────────────────┤
│                  Pipeline 责任链层                             │
│   SSLHandler → Decoder → BusinessHandler → Encoder            │
│   入站: head → tail（next 方向）                               │
│   出站: tail → head（prev 方向）→ writeToSocket               │
├──────────────────────────────────────────────────────────────┤
│                  Channel 通道层                                │
│   AioChannel / NioChannel / UdpChannel                        │
│   统一 API: writeAndFlush / close / starRead                  │
├──────────────────────────────────────────────────────────────┤
│                  I/O 模型层                                    │
│   AIO: AsynchronousSocketChannel + CompletionHandler          │
│        + 共享写线程组（AioWriteThreadGroup）                    │
│   NIO: SocketChannel + Selector + NioEventLoop                │
│   UDP: DatagramChannel + Selector                             │
├──────────────────────────────────────────────────────────────┤
│                  内存管理层                                     │
│   三级池: PoolThreadCache → PoolArena → PoolChunk              │
│   零拷贝: slice / asByteBuffer / readArray                     │
│   引用计数: retain / release 自动归还                          │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 AIO/NIO 双模型

Getty 同时支持 Java AIO（NIO2）和 NIO 两种 I/O 模型，通过不同的 Starter 一行代码切换：

| 特性 | AIO 模型 | NIO 模型 |
|------|---------|---------|
| 底层通道 | `AsynchronousSocketChannel` | `SocketChannel` + `Selector` |
| 读驱动 | OS 回调 `CompletionHandler` | `EventLoop` select 轮询 |
| 写驱动 | 共享写线程组 `AioWriteThreadGroup` | `EventLoop` OP_WRITE 事件 |
| 线程模型 | Boss 线程池 + 共享写线程组 | Accept 线程 + EventLoop 池 |
| 适用场景 | 高并发长连接（万级+） | 中等并发、需要精细控制 |
| 写线程唤醒 | `LockSupport.park/unpark` | Selector `wakeup()` |

**AIO 共享写线程组设计：**
```
Channel-1 ──┐
Channel-2 ──┤── AioWriteThread-1（遍历 + park）
Channel-3 ──┘
Channel-4 ──┐
Channel-5 ──┤── AioWriteThread-2
Channel-6 ──┘
```
- 单个写线程管理多个 Channel，AIO 的 `channel.write()` 是异步提交，OS 并行写出
- 默认 4 个写线程足以支撑万级连接
- 写线程无数据时 `park` 阻塞，零 CPU 空转

**NIO EventLoop 设计：**
```
NioServerStarter
  ├── accept 线程（独立 Selector 接受连接）
  ├── NioEventLoop-0（Selector，管理多个 NioChannel）
  ├── NioEventLoop-1
  └── NioEventLoop-N（轮询负载均衡分配）
```
- 读缓冲区在 EventLoop 级别复用，整个生命周期只分配一次
- selectedKeys 采用 `toArray + clear` 模式，避免迭代器分配
- `SelectedSelector` 包装层检测空轮询 bug，自动修复

### 2.3 责任链管道（Pipeline）

管道是 Getty 事件处理的核心机制，采用**双向链表**结构：

```
入站（读）方向:  head → Decoder → SSLHandler → BusinessHandler → tail
出站（写）方向:  tail → BusinessHandler → SSLHandler → Encoder → head → writeToSocket
```

**事件类型：**

| 事件 | 方向 | 触发时机 |
|------|------|---------|
| `NEW_CHANNEL` | 入站 | 新连接建立 |
| `CHANNEL_READ` | 入站 | 接收到数据 |
| `CHANNEL_CLOSED` | 入站 | 连接关闭 |
| `CHANNEL_EXCEPTION` | 入站 | 发生异常 |
| `CHANNEL_EVENT` | 入站 | 心跳/空闲事件 |
| `CHANNEL_WRITE` | 出站 | 发送数据 |

**处理器继承体系：**
```
ChannelHandler（包级接口）
  └── ChannelBoundHandler（用户接口）
        └── ChannelHandlerAdapter（透传适配器）
              ├── ChannelInboundHandlerAdapter（入站基类）
              │     └── SimpleChannelInboundHandler<T>（泛型简化）
              ├── ByteToMessageDecoder（解码器基类）
              ├── MessageToByteEncoder（编码器基类）
              └── ChannelAllBoundHandlerAdapter（双向处理器）
```

用户只需继承适配器并覆盖感兴趣的方法，无需实现全部接口。

---

## 三、核心优势

### 3.1 AIO 原生支持

- **真正的异步 I/O**：基于 Java NIO2 `AsynchronousSocketChannel`，读写操作由 OS 内核异步完成
- **CompletionHandler 回调**：读完成立即触发管道处理，零轮询开销
- **共享写线程组**：4 个写线程通过 `LockSupport.park/unpark` 驱动万级 Channel 写出，无空转
- **WritePendingException 安全处理**：`AtomicBoolean writeInFlight` 状态机保证异步写操作的串行安全

### 3.2 责任链模式 — 极致可扩展

- **双向链表管道**：入站从 head→tail，出站从 tail→head，编解码自然分层
- **热插拔处理器**：运行时 `addFirst` / `addLast` 动态增删处理器
- **统一事件分发**：`channelProcess()` 统一入口，`ChannelState` 枚举驱动分发
- **零侵入扩展**：用户只需继承 `ChannelHandlerAdapter` 覆盖感兴趣的方法
- **SSL 即插即用**：`SSLHandler` 作为管道处理器，加到链首即自动加解密

```java
// 3 行代码搭建完整通信管道
channelInitializer(channel -> {
    channel.getChannelPipeline()
        .addLast(new MyDecoder())       // 入站解码
        .addLast(new MyEncoder())       // 出站编码
        .addLast(new MyBusinessHandler()); // 业务逻辑
});
```

### 3.3 极致性能

| 优化点 | 实现方式 | 效果 |
|--------|---------|------|
| **三级内存池** | ThreadCache → Arena → Chunk | 同线程分配 ~0ns，无锁竞争 |
| **无锁 MPSC 回收** | 跨线程释放走 CAS 队列 | ~5ns 延迟，零对象分配 |
| **二叉树 O(log N) 分配** | PoolChunk 完全二叉树 + 位图子页 | 高效内存管理，低碎片 |
| **零拷贝** | slice / asByteBuffer / readArray | 避免数据拷贝，减少 GC |
| **引用计数自动归还** | retain/release + 自动 recycle | 无内存泄漏 |
| **EventLoop 读缓冲区复用** | 整个 EventLoop 生命周期复用 | 避免每次 read 分配/释放 |
| **Gathering Write** | `channel.write(ByteBuffer[])` 批量写出 | 减少系统调用次数 |
| **writeViews 预分配** | ByteBuffer[] 数组复用，不够时双倍扩容 | 避免每次写出分配数组 |
| **volatile 标志优化** | `writeInterestSet` 跳过 interestOps 调用 | ~200ns → ~2ns |
| **SelectedSelector 空轮询修复** | 检测并修复 Selector 空轮询 bug | 避免 CPU 100% |
| **时间轮定时器** | HashedWheelTimer O(1) 调度 | 高效心跳/超时检测 |
| **NioChannel writeViews 预分配** | ByteBuffer[] 复用 | 零分配批量写出 |

### 3.4 统一通道抽象

`AbstractSocketChannel` 抽象基类屏蔽了 AIO/NIO/UDP 的差异，上层代码面向统一 API 编程：

```
AbstractSocketChannel
  ├── AioChannel    （AIO 异步通道）
  ├── NioChannel    （NIO 非阻塞通道）
  └── UdpChannel    （UDP 数据报通道）
```

统一 API：
- `writeAndFlush(obj)` — 经管道编码后写出
- `write(obj)` / `flush()` — 分离写与刷新
- `close()` — 安全关闭，触发监听器
- `starRead()` — 启动读取
- `getChannelPipeline()` — 获取责任链管道
- `getChannelAttribute()` — 通道属性绑定

### 3.5 流控与高可用

- **高低水位流控**：`highWaterMark` / `lowWaterMark` 自动标记 `writeable` 状态，防止写缓冲区溢出
- **ChannelGroup 广播**：`ConcurrentHashMap` 存储，`writeToAll()` 一键广播，通道关闭自动移除
- **SSL/TLS 加密**：`SSLHandler` 管道处理器，支持 TLS 1.2，自动握手
- **通道属性**：`ConcurrentSafeMap` 读写安全，支持业务层绑定任意数据
- **关闭监听器**：`CopyOnWriteArrayList` 零锁遍历，支持多监听器（多 ChannelGroup）

### 3.6 丰富的工具集

| 工具 | 说明 |
|------|------|
| `HashedWheelTimer` | 时间轮定时器，O(1) 调度，适用于心跳/超时 |
| `MpscRecycleQueue` | 无锁 MPSC 队列，跨线程零分配回收 |
| `ConcurrentSafeMap` | StampedLock 乐观读并发 Map |
| `FastArrayList` | 高性能列表，内联扩容 |
| `AutoByteBuffer` | 自动扩容缓冲区，指数增长 |
| `Base64` / `MD5` / `GZipUtil` | 自研实现，零第三方依赖 |
| `DateTimeUtil` / `CharsetUtil` | 常用工具 |

---

## 四、快速开始

### 4.1 AIO 服务端

```java
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(channel -> {
    channel.getChannelPipeline()
        .addLast(new StringDecoder())
        .addLast(new StringEncoder())
        .addLast(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel ch, String msg) {
                ch.writeAndFlush("echo: " + msg);
            }
        });
});
server.start();
```

### 4.2 NIO 客户端

```java
NioClientStarter client = new NioClientStarter("127.0.0.1", 8888);
client.channelInitializer(channel -> {
    channel.getChannelPipeline()
        .addLast(new StringDecoder())
        .addLast(new StringEncoder())
        .addLast(new SimpleChannelInboundHandler<String>() {
            @Override
            public void channelRead0(AbstractSocketChannel ch, String msg) {
                System.out.println("received: " + msg);
            }
        });
});
client.start();
```

### 4.3 UDP 通道

```java
NioServerStarter server = new NioServerStarter(9999);
server.socketMode(SocketMode.UDP);
server.channelInitializer(channel -> {
    channel.getChannelPipeline()
        .addLast(new MyUdpHandler());
});
server.start();
```

---

## 五、设计哲学

1. **AIO 优先**：优先使用 Java AIO 获取更低延迟和更高并发，NIO 作为兼容备选
2. **零拷贝**：全链路减少数据拷贝，从内存池到管道到通道，能共享就共享
3. **零分配**：预分配 + 复用 + 池化，最大限度减少 GC 压力
4. **责任链解耦**：编解码、SSL、业务逻辑通过管道链式组合，互不侵入
5. **统一抽象**：AIO/NIO/UDP 共享同一套 API 和管道机制，用户无感切换
6. **自研替代**：基础工具（Base64、MD5、时间轮等）全部自研，零第三方依赖
