# 协议编解码：字符串与 HTTP

本页介绍 Getty 中字符串协议和 HTTP 协议的编解码使用方式。

## 字符串协议

### 分隔符分帧器：DelimiterFrameDecoder

按指定分隔符切分粘包/半包，将完整消息传递给下游解码器。

```java
// 按 "\r\n" 分帧（最常用）
pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));

// 按自定义分隔符分帧
pipeline.addLast(new DelimiterFrameDecoder("\n".getBytes()));
```

### 定长帧分帧器：FixedLengthFrameDecoder

按固定长度切分消息。

```java
// 每条消息固定 100 字节
pipeline.addLast(new FixedLengthFrameDecoder(100));
```

### 字符串编解码器

```java
// 出站：String → byte[]
pipeline.addLast(new StringEncoder());

// 入站：byte[] → String
pipeline.addLast(new StringDecoder());
```

### 完整字符串通信管道

```java
pipeline.addLast(new StringEncoder());                                          // 出站编码
pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER)); // 入站分帧
pipeline.addLast(new StringDecoder());                                          // 入站解码
pipeline.addLast(new MyBusinessHandler());                                      // 业务处理
```

**注意：** 发送消息时需要手动添加分隔符：

```java
channel.writeAndFlush("Hello\r\n");  // 末尾加 "\r\n"
```

## HTTP 协议

### 处理器

| 处理器 | 方向 | 说明 |
|--------|------|------|
| `HttpRequestDecoder` | 入站 | HTTP 请求解码器（服务端使用） |
| `HttpResponseEncoder` | 出站 | HTTP 响应编码器（服务端使用） |
| `HttpClientCodec` | 双向 | HTTP 客户端编解码器（客户端使用） |

### HTTP 服务端

```java
AioServerStarter server = new AioServerStarter(8888);
server.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    
    // HTTP 编解码器
    pipeline.addLast(new HttpResponseEncoder());
    pipeline.addLast(new HttpRequestDecoder());
    
    // 业务处理器
    pipeline.addLast(new SimpleChannelInboundHandler<HttpMessage>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, HttpMessage msg) {
            // 获取请求方法
            String method = msg.getMethod();     // GET, POST 等
            String uri = msg.getUri();           // /api/users
            String version = msg.getHttpVersion(); // HTTP/1.1
            
            // 获取请求头
            HttpHeaders headers = msg.getHeaders();
            String contentType = headers.get("Content-Type");
            
            // 获取请求体
            HttpBody body = msg.getBody();
            String bodyStr = body.getString(StandardCharsets.UTF_8);
            
            // 构造响应
            HttpMessage response = new HttpMessage();
            response.setHttpVersion("HTTP/1.1");
            response.setStatus(200);
            response.setStatusDescription("OK");
            response.getHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            response.setBody(new HttpBody("Hello, HTTP!".getBytes(StandardCharsets.UTF_8)));
            
            // 发送响应
            ch.writeAndFlush(response);
        }
    });
});
server.start();
```

### HTTP 客户端

```java
AioClientStarter client = new AioClientStarter("127.0.0.1", 8888);
client.channelInitializer(channel -> {
    ChannelPipeline pipeline = channel.getChannelPipeline();
    pipeline.addLast(new HttpResponseEncoder());
    pipeline.addLast(new HttpRequestDecoder());
    pipeline.addLast(new SimpleChannelInboundHandler<HttpMessage>() {
        @Override
        public void channelRead0(AbstractSocketChannel ch, HttpMessage msg) {
            System.out.println("响应状态: " + msg.getStatus());
            System.out.println("响应体: " + msg.getBody().getString(StandardCharsets.UTF_8));
        }
    });
});

client.start(new ConnectHandler() {
    @Override
    public void onCompleted(AbstractSocketChannel channel) {
        // 构造 GET 请求
        HttpMessage request = new HttpMessage();
        request.setMethod("GET");
        request.setUri("/api/hello");
        request.setHttpVersion("HTTP/1.1");
        request.getHeaders().set("Host", "127.0.0.1:8888");
        channel.writeAndFlush(request);
    }

    @Override
    public void onFailed(Throwable exc) {
        System.err.println("连接失败: " + exc.getMessage());
    }
});
```

### HttpMessage 对象结构

```
HttpMessage
├── method          请求方法 (GET/POST/PUT/DELETE...)
├── uri             请求 URI
├── httpVersion     HTTP 版本 (HTTP/1.1)
├── status          响应状态码 (200/404/500...)
├── statusDescription  状态描述 (OK/Not Found...)
├── headers         HttpHeaders (请求/响应头)
│   ├── get(name)           获取头部值
│   ├── set(name, value)    设置头部值
│   └── contains(name)      是否包含头部
└── body            HttpBody (请求/响应体)
    ├── getBytes()          获取字节数组
    └── getString(charset)  获取字符串
```

### 支持的测试路径

参考 `getty-example/http` 模块，支持以下场景：

| 路径 | 说明 |
|------|------|
| `GET /` | 基本 GET 请求 |
| `GET /params?id=1&name=test` | URL 查询参数 |
| `POST /form` | application/x-www-form-urlencoded 表单 |
| `POST /upload` | multipart/form-data 文件上传 |
| `GET /headers` | 自定义头部回显 |
| `GET /keep-alive` | Keep-Alive 连接测试 |
| `GET /json` | JSON 响应 |

## 下一步

- [协议编解码：WebSocket、MQTT 与 Protobuf](协议编解码：WebSocket与MQTT与Protobuf) — 其他协议
- [SSL/TLS 加密通信](SSL-TLS加密通信) — HTTPS 配置
- [心跳与断线重连](心跳与断线重连) — 连接保活
