# Getty 使用文档

[**中文**](Home) | [English](Home-EN)

欢迎使用 Getty —— 一个完全基于 Java NIO/AIO 实现的高性能网络框架。

## 简介

Getty 同时支持 AIO（NIO2）和 NIO 两种 I/O 模型，设计借鉴 Netty，提供责任链管道、三级内存池、零拷贝等核心能力，同时保持轻量和易读。

### 模块说明

| 模块 | 说明 |
|------|------|
| **getty-core** | 核心引擎：通道管理、三级内存池、事件循环、责任链管道、SSL、工具集 |
| **getty-expansion** | 可插拔扩展：HTTP / WebSocket / MQTT / Protobuf / 字符串编解码、心跳保活、断线重连、IP 过滤、流量统计 |
| **getty-example** | 使用示例：字符串、HTTP、WebSocket、MQTT、Protobuf 等场景演示 |

## 文档目录

- [快速开始](快速开始)
- [架构概览](架构概览)
- [核心概念：Channel 与 Pipeline](核心概念：Channel与Pipeline)
- [配置参考：GettyConfig](配置参考：GettyConfig)
- [AIO 编程指南](AIO编程指南)
- [NIO 编程指南](NIO编程指南)
- [UDP 通信指南](UDP通信指南)
- [协议编解码：字符串与 HTTP](协议编解码：字符串与HTTP)
- [协议编解码：WebSocket、MQTT 与 Protobuf](协议编解码：WebSocket与MQTT与Protobuf)
- [SSL/TLS 加密通信](SSL-TLS加密通信)
- [高级特性：ChannelGroup 与流控](高级特性：ChannelGroup与流控)
- [心跳与断线重连](心跳与断线重连)

## 环境要求

- **JDK**：Java 8 或更高版本
- **构建工具**：Maven 3.x
- **无第三方运行时依赖**（slf4j 为可选日志门面）

## Maven 引入

```xml
<!-- 核心引擎（必选） -->
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-core</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- 协议编解码扩展（可选） -->
<dependency>
    <groupId>com.gettyio</groupId>
    <artifactId>getty-expansion</artifactId>
    <version>3.0.0</version>
</dependency>
```

## 最简示例

```java
// 3 行代码启动一个 Echo 服务端
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(ch -> {
    ch.getChannelPipeline()
      .addLast(new StringEncoder())
      .addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER))
      .addLast(new StringDecoder())
      .addLast(new SimpleChannelInboundHandler<String>() {
          @Override
          public void channelRead0(AbstractSocketChannel ch, String msg) {
              ch.writeAndFlush("echo: " + msg + "\r\n");
          }
      });
});
server.start();
```

## 许可证

Apache License 2.0
