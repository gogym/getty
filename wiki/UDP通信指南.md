# UDP 通信指南

本页介绍如何使用 Getty 进行 UDP 通信。

## UDP 概述

Getty 的 UDP 通信基于 NIO 的 `DatagramChannel`，使用方式与 TCP 几乎相同，只需将 `socketMode` 设置为 `UDP`。

**特点：**
- 无连接，不需要 accept 过程
- 数据以 `DatagramPacket` 为单位收发
- 支持广播和多播
- 使用 `NioServerStarter` / `NioClientStarter`，设置 `SocketMode.UDP`

## 服务端

```java
NioServerStarter server = new NioServerStarter(9999);
server.socketMode(SocketMode.UDP);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    // UDP 使用专用的 DatagramPacket 编解码器
    pipeline.addLast(new DatagramPacketEncoder());
    pipeline.addLast(new DatagramPacketDecoder());
    // 业务处理器
    pipeline.addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
        @Override
        public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
            // 读取数据
            String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            System.out.println("收到 UDP 数据: " + msg);
            
            // 回复（需要指定目标地址）
            byte[] response = ("Echo: " + msg).getBytes(StandardCharsets.UTF_8);
            DatagramPacket reply = new DatagramPacket(
                response, response.length,
                packet.getSocketAddress()
            );
            channel.writeAndFlush(reply);
        }
    });
});
server.start();
```

## 客户端

```java
NioClientStarter client = new NioClientStarter("127.0.0.1", 9999);
client.socketMode(SocketMode.UDP);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    pipeline.addLast(new DatagramPacketEncoder());
    pipeline.addLast(new DatagramPacketDecoder());
    pipeline.addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
        @Override
        public void channelRead0(AbstractSocketChannel channel, DatagramPacket packet) {
            String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            System.out.println("收到回复: " + msg);
        }
    });
});

client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        // 发送 UDP 数据报
        byte[] data = "Hello UDP".getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
            data, data.length,
            new InetSocketAddress("127.0.0.1", 9999)
        );
        channel.writeAndFlush(packet);
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("启动失败: " + exc.getMessage());
    }
});
```

## DatagramPacket 编解码器

| 处理器 | 方向 | 说明 |
|--------|------|------|
| `DatagramPacketEncoder` | 出站 | 将 `DatagramPacket` 编码为字节写出 |
| `DatagramPacketDecoder` | 入站 | 将接收到的字节封装为 `DatagramPacket` |

## 注意事项

1. **UDP 无连接**：客户端 `start()` 后 `onCompleted` 回调表示通道已就绪，可以立即发送数据
2. **数据报大小**：UDP 安全上限约 60KB，建议单条消息不超过 512 字节以保证网络兼容性
3. **不保证送达**：UDP 不保证消息到达，需业务层自行处理丢包
4. **目标地址**：发送时必须指定目标 `InetSocketAddress`

## 完整示例

参考 `getty-example/string` 模块中的 `UdpServer` 和 `UdpClient`。

## 下一步

- [协议编解码：字符串与 HTTP](协议编解码：字符串与HTTP) — TCP 字符串协议
- [AIO 编程指南](AIO编程指南) — TCP AIO 模型
- [NIO 编程指南](NIO编程指南) — TCP NIO 模型
