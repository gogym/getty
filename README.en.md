# Getty

[中文](README.md) | **English**

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

## Brief Introduction

Getty is a high-performance networking framework built entirely on Java NIO/AIO, supporting both AIO (NIO2) and NIO I/O models.

- Ready for use in production projects, and also helps you deeply understand the underlying principles of Java NIO/AIO
- Fully open source, released under the **Apache License 2.0**
- Lowers the learning cost of Java Socket NIO and improves productivity

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      User Business Code                      │
│  Extend SimpleChannelInboundHandler for business logic       │
├──────────────────────────────────────────────────────────────┤
│              Pipeline (Chain of Responsibility)              │
│   SSLHandler → Decoder → BusinessHandler → Encoder           │
│   Inbound:  head → tail (next direction)                     │
│   Outbound: tail → head (prev direction) → writeToSocket     │
├──────────────────────────────────────────────────────────────┤
│                       Channel Layer                          │
│   AioChannel / NioChannel / UdpChannel                       │
│   Unified API: writeAndFlush / close / starRead              │
├──────────────────────────────────────────────────────────────┤
│                       I/O Model Layer                        │
│   AIO: AsynchronousSocketChannel + CompletionHandler         │
│        + shared write thread group (AioWriteThreadGroup)     │
│   NIO: SocketChannel + Selector + NioEventLoop               │
│   UDP: DatagramChannel + Selector                            │
├──────────────────────────────────────────────────────────────┤
│                      Memory Pool Layer                       │
│   Three-level pool: PoolThreadCache → PoolArena → PoolChunk  │
│   Zero-copy: slice / asByteBuffer / readArray                │
│   Reference counting: retain / release auto-return           │
└──────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Description |
|------|------|
| **getty-core** | Core engine: channel management, three-level memory pool, event loop, chain-of-responsibility pipeline, SSL, utilities |
| **getty-expansion** | Pluggable extensions: HTTP / WebSocket / MQTT / Protobuf / string codecs, heartbeat, reconnection, IP filtering, traffic statistics |
| **getty-example** | Usage examples: string, HTTP, WebSocket, MQTT, Protobuf and more scenarios |

---

## Features

### AIO / NIO Dual Models

Getty supports both Java AIO and NIO I/O models — switch with a single line of code via different Starters:

| Feature | AIO Model | NIO Model |
|------|---------|---------|
| Underlying channel | `AsynchronousSocketChannel` | `SocketChannel` + `Selector` |
| Read driver | OS callback `CompletionHandler` | `EventLoop` select polling |
| Write driver | Shared write thread group `AioWriteThreadGroup` | `EventLoop` OP_WRITE |
| Best for | High-concurrency long-lived connections (10K+) | Medium concurrency, fine-grained control |

**AIO shared write thread group:** a single write thread manages multiple channels; AIO's `channel.write()` submits asynchronously and the OS writes out in parallel. Four write threads by default are enough to support tens of thousands of connections; when there is no data, threads `park` and block with zero CPU spinning.

**NIO EventLoop:** read buffers are reused at the EventLoop level, selectedKeys uses the `toArray + clear` pattern to avoid iterator allocation, and `SelectedSelector` automatically detects and recovers from the Selector empty-poll bug.

### Three-Level Memory Pool

```
PoolThreadCache (L1 thread-local cache)
    ↓ miss
PoolArena (L2 arena manager)
    ↓ miss
PoolChunk (L3 binary-tree memory chunk, 16MB by default)
```

- Same-thread allocation ~0ns, no lock contention
- Cross-thread release goes through a lock-free MPSC queue, ~5ns CAS latency
- Binary-tree O(log N) allocate/free, low fragmentation
- Reference counting `retain/release`, buffers automatically returned to the pool when the count reaches zero

### Chain-of-Responsibility Pipeline

```
Inbound (read):  head → Decoder → SSLHandler → BusinessHandler → tail
Outbound (write):  tail → BusinessHandler → SSLHandler → Encoder → head → writeToSocket
```

- Doubly-linked list structure, codecs naturally layered
- Hot-swappable handlers, dynamically added/removed at runtime
- Unified event dispatch driven by the `ChannelState` enum
- SSL plug-and-play: add it to the head of the chain and encryption/decryption happens automatically

```java
// Build a complete communication pipeline in 3 lines
channel.getChannelPipeline()
    .addLast(new MyDecoder())          // Inbound decode
    .addLast(new MyEncoder())          // Outbound encode
    .addLast(new MyBusinessHandler()); // Business logic
```

### Ultimate Performance

| Optimization | Implementation | Effect |
|--------|---------|------|
| Three-level memory pool | ThreadCache → Arena → Chunk | Same-thread ~0ns, no lock contention |
| Lock-free MPSC recycling | Cross-thread release via CAS queue | ~5ns, zero object allocation |
| Zero-copy | slice / asByteBuffer / readArray | Avoids data copies, reduces GC |
| Gathering Write | `channel.write(ByteBuffer[])` batch write-out | Fewer system calls |
| Pre-allocated writeViews | ByteBuffer[] reuse, double growth | Zero-allocation batch write-out |
| volatile flag optimization | `writeInterestSet` skips interestOps | ~200ns → ~2ns |
| Timing wheel timer | HashedWheelTimer O(1) scheduling | Efficient heartbeat/timeout detection |
| SelectedSelector | Detects and recovers from Selector empty-poll bug | Avoids 100% CPU |

### Unified Channel Abstraction

`AbstractSocketChannel` hides the differences between AIO / NIO / UDP so upper layers program against a unified API:

- `writeAndFlush(obj)` — encode through the pipeline then write out
- `close()` — safe close, triggers listeners
- `getChannelPipeline()` — get the chain-of-responsibility pipeline
- `getChannelAttribute()` — per-channel attribute binding

### Rich Protocol Extensions (getty-expansion)

All extensions are assembled as pipeline handlers — enable them with `addLast(new XxxHandler())`:

| Protocol/Feature | Handlers | Description |
|-----------|--------|------|
| String framing | `DelimiterFrameDecoder` / `FixedLengthFrameDecoder` | Delimiter / fixed-length frame splitting |
| HTTP | `HttpRequestDecoder` / `HttpResponseEncoder` | Full HTTP protocol stack |
| WebSocket | `WebSocketDecoder` / `WebSocketEncoder` | RFC 6455 + Hixie-76 |
| MQTT | `MqttDecoder` / `MqttEncoder` | Complete MQTT 3.1.1 messages |
| Protobuf | `ProtobufDecoder` / `ProtobufEncoder` | Varint32 length framing + codecs |
| Heartbeat | `IdleStateHandler` + `HeartBeatTimeOutHandler` | Timing-wheel O(1) idle detection |
| Reconnection | `ReConnectHandler` | AIO/NIO dual mode, linearly increasing delay |
| IP filtering | `IpFilterRuleHandler` | Pre-computed long range matching |
| Traffic statistics | `ChannelTrafficShapingHandler` | AtomicLong counters + scheduled callback |

### Other Features

- **TCP + UDP unified support**, used in almost the same way
- **Android compatible**: runs directly on Android 4.4+
- **Zero third-party runtime dependencies**: Base64, MD5, timing wheel and other basics are all built in-house
- **API highly similar to Netty** — if you know Netty, there is almost nothing new to learn

---

## Quick Start

### Maven

Add the dependency to the `dependencies` section of your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-core</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Optional: protocol codec extensions -->
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-expansion</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.gettyio:getty-core:3.0.0'
implementation 'com.gettyio:getty-expansion:3.0.0'
```

### AIO Server

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

### NIO Client

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

### UDP Channel

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

## Why Getty

1. As a programmer, I enjoy writing code in my spare time, especially network programming.

2. The JDK provides a powerful NIO library, along with AIO corresponding to UNIX-style event-driven network programming, which makes implementing a networking framework relatively straightforward.

3. I like Netty — both its performance and its programming philosophy (an open-source Java networking framework provided by JBOSS with stable and powerful performance).

4. Why build another wheel when Netty already exists? First, I enjoy building wheels. Second, after years of evolution Netty's ecosystem has become quite large and its codebase bloated, and its profound design philosophy is hard to fully grasp. So I decided to build a lightweight, easy-to-understand version instead.

---

## Documentation

For more details, see the **wiki**: [wiki](https://gitee.com/kokjuis/getty/wikis/pages) · [English wiki](https://github.com/gogym/getty/wiki/Home-EN)

---

## Issues & Feedback

- [Gitee issue](https://gitee.com/kokjuis/getty/issues)
- [Github issue](https://github.com/gogym/getty/issues)

---

## License

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

## Author

**gogym gong**

**email: 189155278@qq.com**

### Getty Community Group 1: 708758323

Give the project a star before joining :)
