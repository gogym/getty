# 协议编解码：WebSocket、MQTT 与 Protobuf

本页介绍 Getty 中 WebSocket、MQTT 和 Protobuf 三种协议的使用方式。

## WebSocket

### 处理器

| 处理器 | 方向 | 说明 |
|--------|------|------|
| `WebSocketEncoder` | 出站 | WebSocket 帧编码 |
| `WebSocketDecoder` | 入站 | WebSocket 帧解码 |
| `WebSocketHandShake` | — | WebSocket 握手处理 |

### WebSocket 服务端

```java
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    pipeline.addLast(new WebSocketEncoder());
    pipeline.addLast(new WebSocketDecoder());
    pipeline.addLast(new SimpleChannelInboundHandler<WebSocketRequest>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, WebSocketRequest msg) {
            // 收到 WebSocket 文本消息
            System.out.println("收到: " + msg.getContent());
            // 回复
            ch.writeAndFlush("echo: " + msg.getContent());
        }
    });
});
server.start();
```

### WebSocket 客户端

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 8888);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    pipeline.addLast(new WebSocketEncoder());
    pipeline.addLast(new WebSocketDecoder());
    pipeline.addLast(new SimpleChannelInboundHandler<WebSocketRequest>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, WebSocketRequest msg) {
            System.out.println("收到: " + msg.getContent());
        }
    });
});
client.start(connectHandler);
```

### 支持的帧类型

- 文本帧（Text Frame）
- 二进制帧（Binary Frame）
- Ping/Pong 帧
- Close 帧

## MQTT

### 处理器

| 处理器 | 方向 | 说明 |
|--------|------|------|
| `MqttEncoder` | 出站 | MQTT 报文编码（单例 `MqttEncoder.INSTANCE`） |
| `MqttDecoder` | 入站 | MQTT 3.1.1 报文解码 |

### MQTT 服务端

```java
AioServerStarter server = new AioServerStarter(9999);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // MQTT 编解码器
    pipeline.addLast(MqttEncoder.INSTANCE);
    pipeline.addLast(new MqttDecoder());
    
    // 业务处理器
    pipeline.addLast(new SimpleChannelInboundHandler<MqttMessage>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, MqttMessage msg) {
            MqttFixedHeader fixedHeader = msg.getFixedHeader();
            System.out.println("消息类型: " + fixedHeader.getMessageType());
            
            // 根据消息类型处理
            switch (fixedHeader.getMessageType()) {
                case CONNECT:
                    // 处理连接请求
                    MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                        .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                        .build();
                    ch.writeAndFlush(connAck);
                    break;
                case PUBLISH:
                    // 处理发布消息
                    System.out.println("Topic: " + msg.getVariableHeader());
                    break;
                case PINGREQ:
                    // 响应心跳
                    ch.writeAndFlush(MqttMessageBuilders.pingResp().build());
                    break;
                case DISCONNECT:
                    ch.close();
                    break;
            }
        }
    });
});
server.start();
```

### MQTT 客户端

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 9999);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    pipeline.addLast(MqttEncoder.INSTANCE);
    pipeline.addLast(new MqttDecoder());
    pipeline.addLast(new SimpleChannelInboundHandler<MqttMessage>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, MqttMessage msg) {
            System.out.println("收到 MQTT 消息: " + msg.getFixedHeader().getMessageType());
        }
    });
});

client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        // 发送 CONNECT 报文
        MqttConnectMessage connect = MqttMessageBuilders.connect()
            .clientId("client-001")
            .protocolVersion(MqttVersion.MQTT_3_1_1)
            .cleanSession(true)
            .keepAlive(60)
            .build();
        channel.writeAndFlush(connect);
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

### 支持的 MQTT 报文类型

| 报文类型 | 说明 |
|---------|------|
| CONNECT | 客户端连接请求 |
| CONNACK | 服务端连接确认 |
| PUBLISH | 发布消息 |
| PUBACK | 发布确认（QoS 1） |
| PUBREC / PUBREL / PUBCOMP | QoS 2 流程 |
| SUBSCRIBE | 订阅请求 |
| SUBACK | 订阅确认 |
| UNSUBSCRIBE | 取消订阅 |
| UNSUBACK | 取消订阅确认 |
| PINGREQ / PINGRESP | 心跳请求/响应 |
| DISCONNECT | 断开连接 |

## Protobuf

### 处理器

| 处理器 | 方向 | 说明 |
|--------|------|------|
| `ProtobufEncoder` | 出站 | Protobuf 消息编码 |
| `ProtobufDecoder` | 入站 | Protobuf 消息解码 |
| `ProtobufVarint32FrameDecoder` | 入站 | Varint32 长度帧分帧 |
| `ProtobufVarint32LengthFieldPrepender` | 出站 | Varint32 长度前缀添加 |

### Protobuf 服务端

```java
AioServerStarter server = new AioServerStarter(9999);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // 出站：长度前缀 + Protobuf 编码
    pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast(new ProtobufEncoder());
    
    // 入站：长度帧分帧 + Protobuf 解码
    pipeline.addLast(new ProtobufVarint32FrameDecoder());
    pipeline.addLast(new ProtobufDecoder(
        MessageClass.Message.getDefaultInstance()  // 传入默认消息实例
    ));
    
    // 业务处理器
    pipeline.addLast(new SimpleChannelInboundHandler<MessageClass.Message>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, MessageClass.Message msg) {
            System.out.println("收到 Protobuf 消息: " + msg.getId());
            // 处理业务逻辑...
        }
    });
});
server.start();
```

### Protobuf 客户端

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 9999);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast(new ProtobufEncoder());
    pipeline.addLast(new ProtobufVarint32FrameDecoder());
    pipeline.addLast(new ProtobufDecoder(
        MessageClass.Message.getDefaultInstance()
    ));
    pipeline.addLast(new SimpleChannelInboundHandler<MessageClass.Message>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, MessageClass.Message msg) {
            System.out.println("收到回复: " + msg.getId());
        }
    });
});

client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        // 构造并发送 Protobuf 消息
        MessageClass.Message msg = MessageClass.Message.newBuilder()
            .setId(1)
            .setReqType(1)
            .setMsgTime(System.currentTimeMillis())
            .setSenderId("user-001")
            .setSenderName("张三")
            .build();
        channel.writeAndFlush(msg);
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

### Protobuf 管道配置顺序

```
出站方向（addLast 顺序）:
  1. ProtobufVarint32LengthFieldPrepender  — 添加 Varint32 长度前缀
  2. ProtobufEncoder                        — 序列化为字节

入站方向（addLast 顺序）:
  1. ProtobufVarint32FrameDecoder           — 按 Varint32 长度帧分帧
  2. ProtobufDecoder                        — 反序列化为 Message 对象
  3. BusinessHandler                        — 业务处理
```

## 下一步

- [SSL/TLS 加密通信](SSL-TLS加密通信) — 加密传输
- [心跳与断线重连](心跳与断线重连) — 连接保活
- [高级特性：ChannelGroup 与流控](高级特性：ChannelGroup与流控) — 广播与流控
