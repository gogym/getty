# 高级特性：ChannelGroup 与流控

本页介绍 Getty 中的 ChannelGroup（通道组广播）和流控（背压）机制。

## ChannelGroup（通道组）

### 概述

`DefaultChannelGroup` 用于管理一组 Channel，支持广播消息到所有通道。内部使用 `ConcurrentHashMap` 存储，读操作无锁。

**特性：**
- 添加通道时自动注册关闭监听器，通道关闭后自动从组中移除
- 广播遍历性能最优（volatile read）
- 弱一致性迭代器，不抛 `ConcurrentModificationException`

### 基本用法

```java
// 创建通道组
DefaultChannelGroup group = new DefaultChannelGroup("all-clients");

// 在服务端处理器中使用
public class ServerHandler extends SimpleChannelInboundHandler<String> {
    
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        // 新连接加入通道组
        group.add(ctx.channel());
        System.out.println("当前在线: " + group.size());
    }
    
    @Override
    public void channelRead0(AbstractSocketChannel ch, String msg) {
        // 广播消息给所有客户端
        group.writeToAll("广播: " + msg + "\r\n");
    }
}
```

### API 参考

| 方法 | 说明 |
|------|------|
| `add(channel)` | 添加通道到组 |
| `remove(channel)` | 从组中移除通道 |
| `find(id)` | 按通道 ID 查找通道 |
| `writeToAll(msg)` | 广播消息到所有通道 |
| `size()` | 获取组中通道数量 |
| `isEmpty()` | 组是否为空 |
| `clear()` | 清空组中所有通道 |
| `contains(channel)` | 是否包含指定通道 |
| `iterator()` | 获取通道迭代器 |

### 多通道组场景

可以创建多个通道组来分类管理连接：

```java
// 按业务分组
DefaultChannelGroup vipGroup = new DefaultChannelGroup("vip");
DefaultChannelGroup normalGroup = new DefaultChannelGroup("normal");

@Override
public void channelRead0(AbstractSocketChannel ch, String msg) {
    // 根据业务逻辑选择广播目标
    if (isVip(ch)) {
        vipGroup.writeToAll(msg + "\r\n");
    } else {
        normalGroup.writeToAll(msg + "\r\n");
    }
}
```

## 流控（背压）

### 概述

Getty 支持基于高低水位线的写出流控，防止写缓冲区无限增长导致 OOM。

**工作原理：**
- 当写队列中的数据量达到**高水位线**时，通道标记为**不可写**
- 当写队列中的数据量降到**低水位线**时，通道恢复为**可写**

### 配置流控

```java
GettyConfig config = new GettyConfig();
config.setFlowControl(true);                    // 开启流控
config.setBufferWriterQueueSize(1024 * 1024);   // 写队列容量 1MB
config.setHighWaterMark(800 * 1024);            // 高水位线 800KB
config.setLowWaterMark(200 * 1024);             // 低水位线 200KB
```

### 流控参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `flowControl` | false | 流控开关 |
| `bufferWriterQueueSize` | 1MB | 写队列总容量 |
| `highWaterMark` | = bufferWriterQueueSize | 高水位线：达到此值标记不可写 |
| `lowWaterMark` | = highWaterMark / 2 | 低水位线：降至此值恢复可写 |

### 检查可写状态

```java
// 在业务处理器中检查通道是否可写
@Override
public void channelRead0(AbstractSocketChannel ch, String msg) {
    if (ch.isWriteable()) {
        // 通道可写，正常发送
        ch.writeAndFlush("response\r\n");
    } else {
        // 通道不可写（写缓冲区已满），可以选择丢弃或缓存
        System.out.println("通道不可写，写缓冲区已满");
    }
}
```

### writeAndFlush 返回值

```java
// writeAndFlush 返回 false 表示被流控拒绝
boolean success = ch.writeAndFlush(msg);
if (!success) {
    // 消息被拒绝，写缓冲区已满
    System.out.println("写出被流控拒绝");
}
```

### 流控使用建议

1. **合理设置水位线**：高水位线不应超过写队列容量的 80%
2. **不可写时的策略**：可以选择丢弃消息、缓存到磁盘、或关闭慢连接
3. **监控写队列**：长时间不可写可能意味着下游消费能力不足
4. **默认关闭**：流控默认关闭，对于大多数场景不需要开启

## 通道属性绑定

除了 ChannelGroup，还可以通过通道属性实现连接级别的状态管理：

```java
// 绑定用户信息
@Override
public void channelAdded(ChannelHandlerContext ctx) {
    ctx.channel().setChannelAttribute("userId", "user-001");
    ctx.channel().setChannelAttribute("loginTime", System.currentTimeMillis());
}

// 读取属性
@Override
public void channelRead0(AbstractSocketChannel ch, String msg) {
    String userId = (String) ch.getChannelAttribute("userId");
    System.out.println("用户 " + userId + " 发送: " + msg);
}
```

## 下一步

- [心跳与断线重连](心跳与断线重连) — 连接保活机制
- [SSL/TLS 加密通信](SSL-TLS加密通信) — 加密传输
- [配置参考：GettyConfig](配置参考：GettyConfig) — 完整配置说明
