# Getty

一个完全基于 Java 实现的高性能网络框架。

A high-performance networking framework based entirely on Java implementations.

---

Copyright 2019 gogym gong

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

## 简介 / Brief Introduction

Getty 是一个完全基于 Java NIO/AIO 封装的高性能网络框架，同时支持 AIO（NIO2）和 NIO 两种 I/O 模型。

Getty is a high-performance networking framework based entirely on Java NIO/AIO, supporting both AIO (NIO2) and NIO I/O models.

- 可在生产项目中使用，也可以帮助你深入理解 Java NIO/AIO 的底层原理
- 完全开源，基于 **Apache License 2.0** 开源协议
- 降低 Java Socket NIO 的学习成本，提高工作效率

---

## 整体架构 / Architecture

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

### 模块组成 / Modules

| 模块 | 说明 |
|------|------|
| **getty-core** | 核心引擎：通道管理、三级内存池、事件循环、责任链管道、SSL、工具集 |
| **getty-expansion** | 可插拔扩展：HTTP / WebSocket / MQTT / Protobuf / 字符串编解码、心跳保活、断线重连、IP 过滤、流量统计 |
| **getty-example** | 使用示例：字符串、HTTP、WebSocket、MQTT、Protobuf 等场景演示 |

---

## Getty 的特点 / Features

### AIO / NIO 双模型

Getty 同时支持 Java AIO 和 NIO 两种 I/O 模型，通过不同的 Starter 一行代码切换：

| 特性 | AIO 模型 | NIO 模型 |
|------|---------|---------|
| 底层通道 | `AsynchronousSocketChannel` | `SocketChannel` + `Selector` |
| 读驱动 | OS 回调 `CompletionHandler` | `EventLoop` select 轮询 |
| 写驱动 | 共享写线程组 `AioWriteThreadGroup` | `EventLoop` OP_WRITE |
| 适用场景 | 高并发长连接（万级+） | 中等并发、精细控制 |

**AIO 共享写线程组：** 单个写线程管理多个 Channel，AIO 的 `channel.write()` 异步提交，OS 并行写出。默认 4 个写线程足以支撑万级连接，无数据时 `park` 阻塞，零 CPU 空转。

**NIO EventLoop：** 读缓冲区在 EventLoop 级别复用，selectedKeys 采用 `toArray + clear` 模式避免迭代器分配，`SelectedSelector` 自动检测修复空轮询 bug。

### 三级内存池

```
PoolThreadCache（L1 线程本地缓存）
    ↓ 未命中
PoolArena（L2 区域管理器）
    ↓ 未命中
PoolChunk（L3 二叉树内存块，默认 16MB）
```

- 同线程分配 ~0ns，无锁竞争
- 跨线程释放走 MPSC 无锁队列，~5ns CAS 延迟
- 二叉树 O(log N) 分配/释放，低碎片
- 引用计数 `retain/release` 归零自动归还池

### 责任链管道（Pipeline）

```
入站（读）:  head → Decoder → SSLHandler → BusinessHandler → tail
出站（写）:  tail → BusinessHandler → SSLHandler → Encoder → head → writeToSocket
```

- 双向链表结构，编解码自然分层
- 热插拔处理器，运行时动态增删
- 统一事件分发，`ChannelState` 枚举驱动
- SSL 即插即用，加到链首即自动加解密

```java
// 3 行代码搭建完整通信管道
channel.getChannelPipeline()
    .addLast(new MyDecoder())         // 入站解码
    .addLast(new MyEncoder())         // 出站编码
    .addLast(new MyBusinessHandler()); // 业务逻辑
```

### 极致性能

| 优化点 | 实现方式 | 效果 |
|--------|---------|------|
| 三级内存池 | ThreadCache → Arena → Chunk | 同线程 ~0ns，无锁竞争 |
| 无锁 MPSC 回收 | 跨线程释放走 CAS 队列 | ~5ns，零对象分配 |
| 零拷贝 | slice / asByteBuffer / readArray | 避免数据拷贝，减少 GC |
| Gathering Write | `channel.write(ByteBuffer[])` 批量写出 | 减少系统调用 |
| writeViews 预分配 | ByteBuffer[] 复用，双倍扩容 | 零分配批量写出 |
| volatile 标志优化 | `writeInterestSet` 跳过 interestOps | ~200ns → ~2ns |
| 时间轮定时器 | HashedWheelTimer O(1) 调度 | 高效心跳/超时检测 |
| SelectedSelector | 检测修复 Selector 空轮询 bug | 避免 CPU 100% |

### 统一通道抽象

`AbstractSocketChannel` 屏蔽 AIO / NIO / UDP 差异，上层面向统一 API 编程：

- `writeAndFlush(obj)` — 经管道编码后写出
- `close()` — 安全关闭，触发监听器
- `getChannelPipeline()` — 获取责任链管道
- `getChannelAttribute()` — 通道属性绑定

### 丰富的协议扩展（getty-expansion）

所有扩展以 Pipeline Handler 形式装配，`addLast(new XxxHandler())` 即可启用：

| 协议/功能 | 处理器 | 说明 |
|-----------|--------|------|
| 字符串帧 | `DelimiterFrameDecoder` / `FixedLengthFrameDecoder` | 分隔符/定长帧分割 |
| HTTP | `HttpRequestDecoder` / `HttpResponseEncoder` | 完整 HTTP 协议栈 |
| WebSocket | `WebSocketDecoder` / `WebSocketEncoder` | RFC 6455 + Hixie-76 |
| MQTT | `MqttDecoder` / `MqttEncoder` | MQTT 3.1.1 完整报文 |
| Protobuf | `ProtobufDecoder` / `ProtobufEncoder` | Varint32 长度帧 + 编解码 |
| 心跳保活 | `IdleStateHandler` + `HeartBeatTimeOutHandler` | 时间轮 O(1) 空闲检测 |
| 断线重连 | `ReConnectHandler` | AIO/NIO 双模式，线性递增延迟 |
| IP 过滤 | `IpFilterRuleHandler` | 预计算 long 值段范围匹配 |
| 流量统计 | `ChannelTrafficShapingHandler` | AtomicLong 计数器 + 定时回调 |

### 其他特性

- **TCP + UDP 统一支持**，使用方式几乎相同
- **Android 兼容**：可直接在 Android 4.4+ 环境使用
- **零第三方依赖**：Base64、MD5、时间轮等基础工具全部自研
- **使用方式与 Netty 高度相似**，有 Netty 经验几乎无需额外学习

---

## 快速开始 / Quick Start

### Maven

在项目的 `pom.xml` 的 `dependencies` 中加入依赖：

```xml
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-core</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- 可选：协议编解码扩展 -->
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-expansion</artifactId>
    <version>2.2.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.gettyio:getty-core:2.2.0'
implementation 'com.gettyio:getty-expansion:2.2.0'
```

### AIO 服务端

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

### NIO 客户端

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

### UDP 通道

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

## 写这个框架的原因 / Why Getty

1. 作为一名程序员，平时喜欢写代码，特别是网络编程方面。

2. JDK 提供了强大的 NIO 类库，并且提供了与 UNIX 网络编程事件驱动 I/O 对应的 AIO，使得实现一套网络通讯框架变得相对简单。

3. 作者喜欢 Netty，无论是其性能还是编程思想（JBOSS 提供的一个 Java 开源网络框架，提供了稳定和强大的性能）。

4. 有了 Netty 为何还要自己造轮子？其一是作者喜欢造轮子。其二，Netty 经过多年的发展，其生态体系已经比较庞大，代码比较臃肿，再者其高深的设计哲学很难悟其精髓。因而索性造一个轻量、易懂的版本。

---

## 更多详情与文档 / Documentation

更多详情，请点击 **wiki**：[wiki](https://gitee.com/kokjuis/getty/wikis/pages)

---

## Bug 反馈与建议 / Issues

- [码云 Gitee issue](https://gitee.com/kokjuis/getty/issues)
- [Github issue](https://github.com/gogym/getty/issues)

---

## 许可证 / License

```
Copyright 2019 gogym gong

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 作者 / Author

**gogym gong**

**email: 189155278@qq.com**

### Getty 交流群 1：708758323

进群先 star 一下哦
