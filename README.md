# getty

一个完全基于java 实现的高性能网络框架。

A high-performance networking framework based entirely on Java implementations.

### 简介 brief introduction

1、getty是完全基于java nio封装的高性能网络框架。

Getty is a high-performance networking framework based entirely on Java NIO encapsulation.

2、getty可在项目中使用，也可以用于帮助你更好的学习java nio

Getty can be used in a project or to help you learn Better about Java NIO

3、getty完全开源，基于 Apache License 2.0 开源协议。

Getty is fully open source, based on the Apache License 2.0 open source License.

4、getty的目的是为了降低对java socket nio的学习成本，帮助提高工作效率。 

Getty aims to reduce the cost of using Java Socket NiO and help improve productivity.

### getty名称的由来 Origin of the name

取名getty主要是作者过去使用netty比较多，对netty表示尊敬，以及getty本身借鉴了netty的设计思想。

其次作者本人姓氏的拼音首字符是“G”，因而取名getty。

The name Getty is mainly due to the author's use of Netty in the past, which shows respect for Netty, and the fact that Getty itself borrows from Netty's design ideas.

Then the author's surname began with the alphabet character "G", hence the name Getty.

### 写这个框架的原因： The reason for writing this framework

1、作为一名程序员，平时喜欢写写代码，特别是网络编程方面。

The author is a programmer, usually like to write code, especially network communication.

2、JDK 提供了强大的NIO类库，并且提供了与UNIX网络编程事件驱动I/O对应的AIO。使得实现一套网络通讯框架变得相对简单。

The JDK provides a powerful NIO class library and provides AIO, which corresponds to UNIX network programming event-driven I/O.It makes it relatively simple to implement a network communication framework.

3、作者喜欢netty，无论是其性能还是编程思想（JBOSS提供的一个java开源网络框架，提供了稳定和强大的性能）

The author likes Netty, both for its performance and programming ideas (JBOSS provides an open source Java networking framework that provides stable and powerful performance).

4、有了netty为何还要自己造轮子？这里有两个原因，其一是作者喜欢造轮子，这是病，治不了。其二，netty经过多年的发展，其生态体系已经比较庞大，其代码比较臃肿，再者其高深的设计哲学，作者这样的凡夫俗子很难悟其精髓。因而索性造一个。

Why build your own wheels when you have Netty?
There are two reasons. One is that I like to build wheels. It's a disease that I can't change.
Second, after many years of development, Netty's ecosystem has become relatively large, its code is relatively bloated, and its profound design philosophy is hard for ordinary people to understand.
So just make one.

### getty的特点： The characteristics of the getty

1、完全基于java nio，基于nio1以及nio2(aio)做了实现，整体代码代码结构很清晰，非常简单易用。

Completely based on Java NIO, based on NIO1 and NIO2 (AIO) to do the implementation, the overall code structure is very lightweight, very easy to use.

2、借鉴了Netty和其他框架的一些优秀设计思想，如责任链、内存池、零复制等优秀设计模式。

Some of the good design ideas of Netty and other frameworks are borrowed, such as responsibility chain, memory pooling, zero copy and other excellent design patterns.

3、Getty为常用的开发场景提供了插件(字符串编解码器，Protobuf编解码器，WebSocket编解码器，MQTT编解码器，心跳超时处理器，IP过滤等)。

Getty provides commonly used plug-ins (String codec, Protobuf codec, WebSocket codec, MQTT codec, heartbeat timeout processor, IP filtering, etc.) for most development scenarios.

4、Getty可在android环境中直接使用(兼容Android4.4或以上版本)

Getty can be used directly in an android environment (compatible with Android4.4 or above)

5、Getty同时提供TCP和UDP支持，并以几乎相同的方式使用它们，极大地提高了易用性。

Getty provides support for both TCP and UDP and USES them in much the same way, greatly improving ease of use.

5、Getty的内置处理器在使用期间支持热交换设计，每个处理器可以灵活组合。

Getty's built-in processor supports the hot-swap design during use, and each processor can be combined flexibly.

6、框架拥有非常好的拓展性，处理器扩展非常简单，大大降低了扩展开发成本。

The framework has a very good scalability, processor scalability is very simple, greatly reducing the cost of expansion development.

7、高效和稳定性能，相同条件下同时发送百万消息的耗时与netty相当。

High efficiency and stable performance, it takes less time than netty to send millions of messages simultaneously under the same conditions.

8、使用方式与netty非常相似，只要有netty是使用经验，使用getty几乎不需要额外学习。

Much like Netty, using Getty requires little extra learning as long as Netty is experienced.

 ### 简单使用 Simple to use

 **Maven** 

在项目的pom.xml的dependencies中加入依赖:

Dependencies are included in the Projects Pom.XML dependencies


```
        <dependency>
            <groupId>com.gettyio</groupId>
            <artifactId>getty-core</artifactId>
            <version>2.1.0-rc</version>
        </dependency>
```

 **Gradle** 


```
compile group: 'com.gettyio', name: 'getty-core', version: '2.1.0-rc'
```


**本次更新**


优化

1、内存池的优化，去除堆外内存的使用，
原因：1、绝大部分场景无需使用堆外内存。2、堆外内存的使用比较复杂，掌控不好容易出现内存溢出。3、降低代码复杂性

3、责任链重新实现，提高了性能和健壮性

3、优化了若干代码，提高易用性

修复

1、修复其他若干bug




 **非Maven项目 (No Maven)** 

到中央仓库下载jar包导入到工程中

Go to the central repository and download the JAR package into the project

链接(link)：https://mvnrepository.com/artifact/com.gettyio/getty-core 

[点击跳转到中央仓库 jump to the central](https://mvnrepository.com/artifact/com.gettyio/getty-core)

 
### 更多详情与文档 More details and documentation

更多详情，请点击( For more details, click here)  **wiki** ：[wiki](https://gitee.com/kokjuis/getty/wikis/pages)

### 提供bug反馈或建议 Provide bug feedback or Suggestions

- [码云Gitee issue](https://gitee.com/kokjuis/getty/issues)
- [Github issue](https://github.com/gogym/getty/issues)

### create by

 **gogym** 

 **email:189155278@qq.com** 
 
 ### getty交流群1 ：708758323       
 进群先star一下哦



