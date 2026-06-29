# getty-expansion — 协议编解码与功能扩展模块

## 1. 模块定位

`getty-expansion` 是 `getty-core` 的**可插拔扩展层**。  
Core 模块提供了通道管理、内存池、事件循环、管道（Pipeline）等基础设施；  
本模块在此基础上，以 **管道处理器（ChannelHandler）** 的形式，提供开箱即用的协议编解码、连接保活、断线重连、IP 过滤、流量统计等能力。

> **设计原则**：所有功能均通过 Pipeline 的 `addFirst / addLast` 装配，无需修改 Core 一行代码。

---

## 2. 目录结构

```
handler/
├── codec/                        ← 协议编解码器
│   ├── string/                   ← 字符串 / 分隔符 / 定长帧
│   ├── http/                     ← HTTP 请求/响应 完整编解码
│   ├── websocket/                ← WebSocket 握手 + 帧编解码
│   ├── mqtt/                     ← MQTT 3.1.1 完整报文编解码
│   ├── protobuf/                 ← Protobuf 长度帧 + 编解码
│   └── datagramPacket/           ← UDP 数据包编解码
├── ipfilter/                     ← IP 黑白名单过滤
├── timeout/                      ← 空闲检测 / 心跳超时 / 断线重连
└── traffic/                      ← 通道级流量统计
```

---

## 3. 协议编解码器

### 3.1 字符串与帧分割（string）

| 处理器 | 职责 |
|--------|------|
| `DelimiterFrameDecoder` | 按分隔符（默认 `\r\n`）切分字节流为独立帧，支持多字节分隔符和跨包匹配 |
| `FixedLengthFrameDecoder` | 按固定长度切分字节流 |
| `StringDecoder` | `byte[]` / `PooledByteBuffer` → `String`（UTF-8） |
| `StringEncoder` | `String` / `byte[]` → `PooledByteBuffer`（从内存池分配） |

**典型管道装配：**

```
pipeline.addLast(new DelimiterFrameDecoder(new byte[]{'\r','\n'}));
pipeline.addLast(new StringDecoder());
pipeline.addLast(new StringEncoder());
pipeline.addLast(new MyBusinessHandler());
```

### 3.2 HTTP（http）

完整的 HTTP 协议栈实现：

- **请求侧**：`HttpRequestDecoder` / `HttpRequestEncoder` / `HttpRequest`
- **响应侧**：`HttpResponseDecoder` / `HttpResponseEncoder` / `HttpResponse` / `HttpResponseStatus`
- **领域模型**：`HttpMessage` → `HttpHeaders`（自定义哈希链表，性能优于 HashMap）→ `HttpBody`
- **辅助类**：`HttpVersion`、`HttpMethod`、`HttpConstants`、序列化器 `HttpDecodeSerializer` / `HttpEncodeSerializer`

### 3.3 WebSocket（websocket）

- **握手**：`WebSocketHandShake` — 解析 HTTP 升级请求，生成 RFC 6455 握手响应，兼容 Hixie-76（版本 0~3）
- **帧类型**：`TextWebSocketFrame`、`BinaryWebSocketFrame`、`PingWebSocketFrame`、`PongWebSocketFrame`、`CloseWebSocketFrame`、`ContinuationWebSocketFrame`
- **编解码**：`WebSocketDecoder` / `WebSocketEncoder`

### 3.4 MQTT（mqtt）

完整的 MQTT 3.1.1 协议实现：

- **消息模型**：`MqttMessage`（统一消息类，按 `MqttMessageType` 区分 14 种报文）
- **固定头部**：`MqttFixedHeader`（消息类型、QoS、Dup、Retain）
- **可变头部**：`MqttConnectVariableHeader`、`MqttConnAckVariableHeader`、`MqttPublishVariableHeader`、`MqttMessageIdVariableHeader`
- **负载**：`MqttConnectPayload`、`MqttSubscribePayload`、`MqttSubAckPayload`、`MqttUnsubscribePayload`
- **编解码**：`MqttDecoder` / `MqttEncoder`
- **构建器**：`MqttMessageBuilders`（流式 API 构建各类 MQTT 消息）
- **枚举**：`MqttQoS`（AT_MOST_ONCE / AT_LEAST_ONCE / EXACTLY_ONCE）、`MqttVersion`、`MqttConnectReturnCode`

### 3.5 Protobuf（protobuf）

- `ProtobufVarint32FrameDecoder` — Varint32 长度帧解码，解决 TCP 粘包/半包
- `ProtobufVarint32LengthFieldPrepender` — 编码时前置 Varint32 长度字段
- `ProtobufDecoder` — 解码为 `MessageLite` 对象，兼容 Protobuf 2.5.0+ Parser API 与低版本 Builder API
- `ProtobufEncoder` — 编码 `MessageLite` 为字节

### 3.6 UDP 数据包（datagramPacket）

- `DatagramPacketDecoder` / `DatagramPacketEncoder` — `DatagramPacket` 的解包与封装

---

## 4. 连接保活与断线重连（timeout）

### 4.1 空闲检测 — `IdleStateHandler`

```
通道读/写 → 重置空闲标志
      ↓
时间轮定时任务 → 检查标志 → 空闲？触发 IdleState 事件 : 标记空闲，重新调度
```

- 基于 `HashedWheelTimer`，调度与取消均为 **O(1)**
- 多个实例可**共享同一个 Timer**，单线程服务数万连接
- 支持读空闲 / 写空闲独立配置
- 通道关闭时精确取消定时任务，共享 Timer 不随单个 Handler 销毁

### 4.2 心跳超时 — `HeartBeatTimeOutHandler`

配合 `IdleStateHandler` 使用：

```
IdleStateHandler 触发 READER_IDLE 事件
      ↓
HeartBeatTimeOutHandler 累加空闲计数
      ↓
超过阈值（默认 3 次）→ 关闭连接
收到有效数据 → 重置计数
```

### 4.3 断线重连 — `ReConnectHandler`

- 仅在**异常断开**时触发（非主动关闭）
- 支持 **AIO / NIO** 双模式重连
- 线性递增延迟策略：`delay = attempts × threshold`
- `AtomicBoolean` 防止并发重连
- 重连成功后自动恢复 SSL 握手
- 时间轮调度，最大重试次数可配置

---

## 5. IP 过滤（ipfilter）

```
新连接建立 → IpFilterRuleHandler.accept()
      ↓
RuleBasedIpFilter.matches() → 预计算的 long[] 数组二分匹配 IP 段
      ↓
ACCEPT 策略：匹配则放行，拒绝则关闭
REJECT 策略：匹配则关闭，放行其余
```

- 构造时预计算 IP 段的 `long` 值，运行时零转换开销
- 支持 IP 地址段范围匹配（`IpRange`）
- 管道级装配，即插即用

---

## 6. 流量统计（traffic）

### `ChannelTrafficShapingHandler`

- 统计通道累计 / 周期内读写字节数与次数
- `AtomicLong` 保证计数器线程安全
- 定时回调 `TrafficShapingHandler` 接口上报吞吐量
- 通道关闭时自动释放定时线程池

---

## 7. 核心优势

| 优势 | 说明 |
|------|------|
| **即插即用** | 所有功能以 Pipeline Handler 形式装配，`addLast(new XxxHandler())` 即可启用 |
| **零侵入** | 不修改 Core 一行代码，完全依赖 Core 的管道接口扩展 |
| **协议全覆盖** | 字符串 / HTTP / WebSocket / MQTT / Protobuf / UDP 六大协议栈开箱即用 |
| **组合灵活** | 编解码器 + 功能处理器可自由组合，按需装配到管道 |
| **性能优先** | IP 过滤预计算、HTTP 头部自定义哈希链表、时间轮 O(1) 调度、零拷贝帧分割 |
| **资源安全** | 共享 Timer 生命周期管理、通道关闭自动取消任务、内存池自动回收 |
| **AIO/NIO 透明** | 断线重连等处理器自动识别通道类型，AIO/NIO 无感切换 |

---

## 8. 典型装配示例

### 8.1 TCP 字符串协议（分隔符 + 心跳）

```java
channelInitializer.setChannelHandler(new ChannelInitializer() {
    @Override
    public void initChannel(AbstractSocketChannel ch) {
        ch.getPipeline().addLast(new DelimiterFrameDecoder(new byte[]{'\r', '\n'}));
        ch.getPipeline().addLast(new StringDecoder());
        ch.getPipeline().addLast(new StringEncoder());
        ch.getPipeline().addLast(new IdleStateHandler(30, 0));
        ch.getPipeline().addLast(new HeartBeatTimeOutHandler());
        ch.getPipeline().addLast(new MyBusinessHandler());
    }
});
```

### 8.2 HTTP 服务

```java
ch.getPipeline().addLast(new HttpRequestDecoder());
ch.getPipeline().addLast(new HttpResponseEncoder());
ch.getPipeline().addLast(new HttpServerHandler());
```

### 8.3 WebSocket

```java
ch.getPipeline().addLast(new WebSocketDecoder());
ch.getPipeline().addLast(new WebSocketEncoder());
ch.getPipeline().addLast(new WebSocketHandler());
```

### 8.4 MQTT 物联网

```java
ch.getPipeline().addLast(new MqttDecoder());
ch.getPipeline().addLast(new MqttEncoder());
ch.getPipeline().addLast(new MqttBrokerHandler());
```

### 8.5 Protobuf + IP 过滤 + 流量统计

```java
ch.getPipeline().addLast(new IpFilterRuleHandler(ipRanges, IpFilterRuleType.ACCEPT));
ch.getPipeline().addLast(new ProtobufVarint32FrameDecoder());
ch.getPipeline().addLast(new ProtobufDecoder(MyProto.getDefaultInstance()));
ch.getPipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
ch.getPipeline().addLast(new ProtobufEncoder());
ch.getPipeline().addLast(new ChannelTrafficShapingHandler(5000, trafficCallback));
ch.getPipeline().addLast(new MyBusinessHandler());
```
