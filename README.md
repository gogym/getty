# getty

一个完全基于java 实现的aio框架。

## 目录
   * 前言
   * 说说写这个框架的原因
   * 说说getty的特点
   * 说说getty的性能和稳定性
   * 如何使用
   * 插件的使用
   * 未完待续。。。

### 前言
1、getty只是本人空闲时间随手写的一个网络通讯框架，虽然本人认为这份代码写的还凑合(当然还有很多可提升的空间)。我也只是站在巨人的肩膀上而已

2、本人建议在生产环境中尽量使用netty等已经非常成熟的框架，getty还有很长的路要走，getty可用在中小项目中使用，也可以用于帮助你更好的学习java aio

3、getty完全开源，基于 Apache License 2.0 开源协议。

4、欢迎大家提供建议或者提交代码分支帮助getty更好的完善提高。

5、如果你觉得getty对你有帮助，请给我一个start鼓励一下。

### 说说写这个框架的原因：

1、作者本人是一个码农，比较喜欢研究技术，特别是网络通讯。

2、JDK1.7升级了NIO类库，升级后的NIO类库被称为NIO 2.0。正式提供了异步文件I/O操作，同时提供了与UNIX网络编程事件驱动I/O对应的AIO。AIO的发布使得实现一套网络通讯框架变得相对简单。但如果你不努力，可能也无法理解哦。

3、本人对netty比较喜欢，无论是其性能还是编程思想（JBOSS提供的一个java开源网络框架，可以说是java网络通讯里的一哥，极其稳定和强大的性能使得被广泛使用）

4、有了netty为何还要自己造轮子？这里有两个原因，其一是本人就喜欢造轮子，这是病，改不了。其二，netty经过多年的发展，其生态体系已经比较庞大，导致其代码比较臃肿，再者其高深的设计哲学我等凡夫俗子很难悟其精髓。因而索性自己造一个。

5、netty毕竟是别人的东西，还是老外的。并且国内也有许多优秀的开源框架，例如t-io也是非常优秀的网络框架。想想，别人都能有，为何我不能直接搞一个呢，于是乎脑袋发热，抽时间造了一个。

### 说说getty的特点：

1、完全基于java aio，整个工程只依赖 slf4j（一个日志的门面框架），对工程几乎没有入侵性。

2、借鉴了netty和其他框架的部分优秀设计思想，如责任链、内存池化、零拷贝等优秀的设计模式。

3、简洁的代码，清晰的注释，以及提供了直接可用的多个插件，只要用过netty，那么学习成本基本为零。

4、可直接在安卓上使用，服务与客户端使用几乎一致（api 26+或android 8.0+）

### 说说getty的性能和稳定性：

硬件条件：cpu：i7-7700 | 内存：16G | 网络：局域网 | 操作系统：win10家庭版 | jdk 8

经过本人简单的测试，整体的性能和稳定性还是不错的：

1、单连接发送一百万条文本消息耗时277毫秒，这个性能总体上还过得去。

![图片名称](https://github.com/gogym/getty/blob/master/img/aaa.png)

2、开启了SSL以后发送一百万条文本消息大概耗时3.8秒，这个性能也算乐观，因为毕竟SSL本身对消息的加密和解密是非常消耗性能的。

![图片名称](https://github.com/gogym/getty/blob/master/img/bbb.png)

3、同时开启10条连接，每条连接发送一百万条文本消息，每条连接平均耗时是比较均衡的，平均三百多毫秒。性能非常可观

![图片名称](https://github.com/gogym/getty/blob/master/img/ccc.png)

4、服务器启动时的内存消耗，启动时内存消耗非常小，占用还不到40m

![图片名称](https://github.com/gogym/getty/blob/master/img/ddd.png)

5、连续发送一百万条消息时的内存消耗，大概消耗160m左右，而且内存回收也非常迅速

![图片名称](https://github.com/gogym/getty/blob/master/img/eee.png)

### 如何使用：

先添加 slf4j 依赖：

```
//java 中使用
  <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
```

安卓中使用添加安卓版本：

```
 // https://mvnrepository.com/artifact/org.slf4j/slf4j-android
    implementation group: 'org.slf4j', name: 'slf4j-android', version: '1.7.26'
```



1、服务器端启动：

```
   AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                //初始化责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();
                //添加结束符处理器
                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //添加string类型消息解码器
                defaultChannelPipeline.addLast(new StringDecoder());
                //自定义的消息处理器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();
```

```
//简单的消息处理器
public class SimpleHandler extends SimpleChannelInboundHandler<String> {
    @Override
    public void channelAdded(AioChannel aioChannel) {
        System.out.println("连接过来了");
    }

    @Override
    public void channelClosed(AioChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AioChannel aioChannel, String str) {
        System.out.println("读取消息:" +str);
        try {
            byte[]  msgBody = (str + "\r\n").getBytes("utf-8");
            //返回消息给客户端
            aioChannel.writeAndFlush(msgBody);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        System.out.println("出错了");
    }
    
}
```

2、客户端启动：

```
     AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                //责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();
                //字符串编码器
                defaultChannelPipeline.addLast(new StringEncoder());
                //指定结束符解码器
                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //字符串解码器
                 defaultChannelPipeline.addLast(new StringDecoder());
                 //定义消息解码器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });

        try {
            client.start();
            Thread.sleep(1000);
            //获取通道
            AioChannel aioChannel = client.getAioChannel();
            
            //发送消息
            String s = "me\r\n";
            byte[] msgBody = s.getBytes("utf-8");
            aioChannel.writeAndFlush(msgBody);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
```

最简单的使用方式就完成了。


### 插件的使用：

1、开启SSL

开启SSL非常简单，getty已经提供了易用且健壮的插件。服务端与客户端唯一的区别就是 setClientMode(),服务器端需设置为false。

```
        AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();
                //获取证书
                String pkPath = ResourceUtils.getURL("classpath:serverStore.jks")
                        .getPath();
                SslConfig sSLConfig = new SslConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(false);
                //设置单向验证
                sSLConfig.setClientAuth(ClientAuth.NONE);
                //初始化ssl服务
                SslService sSLService = new SslService(sSLConfig);
                //把ssl插件注入责任链即可
                defaultChannelPipeline.addFirst(new SslHandler(channel.createSSL(sSLService)));

                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                defaultChannelPipeline.addLast(new StringDecoder());
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();
```

2、开启ip过滤

同样getty已经提供ip过滤插件IpFilterRuleHandler，只需注入到责任链即可

```
      AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                //需要过滤的ip起始段
                IpRange ir = new IpRange("127.0.0.1", "127.0.0.1");
                List<IpRange> list = new ArrayList<>();
                list.add(ir);
                //注入ip过滤器
                defaultChannelPipeline.addLast(new IpFilterRuleHandler(list));

                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                defaultChannelPipeline.addLast(new StringDecoder());
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();
```

3、流量统计插件

getty提供了简单的流量统计插件，可方便统计总的字节以及规定时间内的吞吐量

```
     AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();


                //添加统计插件
                ChannelTrafficShapingHandler channelTrafficShapingHandler=  new ChannelTrafficShapingHandler(10000)
                defaultChannelPipeline.addLast(channelTrafficShapingHandler);
                //获取总读取字节数
                channelTrafficShapingHandler.getTotalRead();

                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                defaultChannelPipeline.addLast(new StringDecoder());
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();
```
4、心跳插件

getty提供了心跳插件HeartBeatTimeOutHandler，可以方便移除空闲的连接

```

        AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                //添加心跳起搏器,设置心跳时间间隔
                defaultChannelPipeline.addLast(new IdleStateHandler(channel, 2, 0));
                //添加心跳插件
                defaultChannelPipeline.addLast(new HeartBeatTimeOutHandler());

                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                defaultChannelPipeline.addLast(new StringDecoder());
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();
```

5、简单好用的自定义解码器

getty目前提供了字符串解码器：定长解码器、指定结束符解码器
添加自定义解码器也非常简单，看源码能非常好理解。

### 未完待续。。。


