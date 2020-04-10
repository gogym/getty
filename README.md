# getty

一个完全基于java 实现的高性能网络框架。

### 简介

1、getty是完全基于java nio封装的高性能网络框架。

2、getty可在项目中使用，也可以用于帮助你更好的学习java nio

3、getty完全开源，基于 Apache License 2.0 开源协议。

4、getty的目的是为了降低对java socket nio的使用成本，帮助提高工作效率。 


### getty名称的由来

取名getty主要是作者过去使用netty比较多，对netty表示尊敬，以及getty本身借鉴了netty的设计思想。

然后作者本人姓氏的拼音首字符是“G”，因而取名getty。

### 说说写这个框架的原因：

1、作者本人是一名程序员，平时喜欢写写代码，特别是网络通讯方面。

2、JDK1.7升级了NIO类库，升级后的NIO类库被称为NIO 2.0。正式提供了异步文件I/O操作，同时提供了与UNIX网络编程事件驱动I/O对应的AIO。AIO的发布使得实现一套网络通讯框架变得相对简单。

3、本人对netty比较喜欢，无论是其性能还是编程思想（JBOSS提供的一个java开源网络框架，可以说是java网络通讯里的一哥，极其稳定和强大的性能使得被广泛使用）

4、有了netty为何还要自己造轮子？这里有两个原因，其一是本人喜欢造轮子，这是病，改不了。其二，netty经过多年的发展，其生态体系已经比较庞大，导致其代码比较臃肿，再者其高深的设计哲学我等凡夫俗子很难悟其精髓。因而索性造一个。

5、netty毕竟是国外的框架。并且国内也有许多优秀的开源框架，想想，为何不能自己也写一个呢？于是业余时间写了getty。


### getty的特点：

1、完全基于java nio，基于nio1以及nio2(aio)做了实现，整体代码代码结构很轻量，也非常简单易用。

2、借鉴了netty和其他框架的部分优秀设计思想，如责任链、内存池化、零拷贝等优秀的设计模式。

3、getty提供了常用的多个插件（String编解码器，protobuf编解码器器，心跳超时处理器、ip过滤，websocket插件等）满足大部分开发场景。

4、getty可直接在安卓环境中使用（兼容Android5.0以上版本）

5、getty同时提供了TCP和UDP的支持，并且使用方式几乎一致，大大提高了易用性。

5、getty内置处理器支持使用过程中的热拔插设计，各处理器可以灵活组合。

6、框架拥有非常好的拓展性，处理器拓展也非常简单，大大降低了开发成本。

7、高效和稳定性能，经过多次测试，同时发送百万消息的耗时居然比netty还好。

相同的条件下发送百万条消息，getty 使用nio2模式耗时500毫秒左右，使用nio1模式耗时1.5秒左右，netty耗时2.5秒左右（也许是netty里面做了很多其他处理）。

8、使用过程与netty非常相似，只要有netty是使用经验，使用getty几乎不需要额外学习。

 ### 简单使用 

 **Maven** 

在项目的pom.xml的dependencies中加入以下内容:


```
        <dependency>
            <groupId>com.gettyio</groupId>
            <artifactId>getty-core</artifactId>
            <version>1.3.3</version>
        </dependency>
```

 **Gradle** 


```
compile group: 'com.gettyio', name: 'getty-core', version: '1.3.3'
```


 **非Maven项目** 

可直接到中央仓库下载jar包导入到工程中

链接：https://mvnrepository.com/artifact/com.gettyio/getty-core [点击跳转到中央仓库](https://mvnrepository.com/artifact/com.gettyio/getty-core)


### 新版本特征

1.3.3是比较用心的一个版本，建议使用
 
 1、添加 nio1 的ssl支持
 
 2、添加启动后回调，方便获取channel
 
 3、优化了代码和注释
 
 4、提高了稳定性和性能


### 更多详情与文档

更多详情，请点击  **wiki文档** ：[跳转到wiki](https://gitee.com/kokjuis/getty/wikis/pages)

### 提供bug反馈或建议

- [码云Gitee issue](https://gitee.com/kokjuis/getty/issues)
- [Github issue](https://github.com/gogym/getty/issues)

### create by

 **gogym** 

 **email:189155278@qq.com** 
 
 ### getty交流群1 ：708758323



