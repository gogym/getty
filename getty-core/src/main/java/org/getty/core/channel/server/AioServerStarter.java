/**
 * 包名：org.getty.core.channel.server
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.server;

import org.getty.core.buffer.ChunkPool;
import org.getty.core.channel.AioChannel;
import org.getty.core.channel.internal.ReadCompletionHandler;
import org.getty.core.channel.internal.WriteCompletionHandler;
import org.getty.core.function.Function;
import org.getty.core.pipeline.ChannelPipeline;
import org.getty.core.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 类名：AioServerStarter.java
 * 描述：aio服务器端
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AioServerStarter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AioServerStarter.class);

    //Server端服务配置
    protected AioServerConfig config = new AioServerConfig();
    //内存池
    protected ChunkPool chunkPool;
    //读回调事件处理
    protected ReadCompletionHandler aioReadCompletionHandler;
    //写回调事件处理
    protected WriteCompletionHandler aioWriteCompletionHandler;
    // 责任链对象
    protected ChannelPipeline channelInitializer;
    //线程池
    private ThreadPool workerThreadPool;
    //为客户端创建回调函数
    private Function<AsynchronousSocketChannel, AioChannel> aioChannelFunction;
    //io服务端
    private AsynchronousServerSocketChannel serverSocketChannel = null;
    //io线程池
    private AsynchronousChannelGroup asynchronousChannelGroup;
    //服务线程运行标志
    private volatile boolean running = true;
    //Boss线程数，获取cpu核心,核心小于4设置线程为3，大于4设置和cpu核心数一致
    private int bossThreadNum = Runtime.getRuntime().availableProcessors() < 4 ? 3 : Runtime.getRuntime().availableProcessors();
    // Boss共享给Worker的线程数，核心小于4设置线程为1，大于4右移两位
    private int bossShareToWorkerThreadNum = bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2;
    // Worker线程数
    private int workerThreadNum = bossThreadNum - bossShareToWorkerThreadNum;

    /**
     * 简单启动
     *
     * @param port 服务端口
     */
    public AioServerStarter(int port) {
        config.setPort(port);
    }

    /**
     * 指定host启动
     *
     * @param host 服务地址
     * @param port 服务端口
     */
    public AioServerStarter(String host, int port) {
        config.setHost(host);
        config.setPort(port);
    }

    /**
     * 指定配置启动
     *
     * @param config
     * @return
     */
    public AioServerStarter(AioServerConfig config) {
        if (config == null) {
            throw new NullPointerException("AioServerConfig can't null");
        }

        if (config.getPort() == 0) {
            throw new NullPointerException("AioServerConfig port can't null");
        }
        this.config = config;
    }

    /**
     * 责任链
     *
     * @param channelInitializer
     * @return
     */
    public AioServerStarter channelInitializer(ChannelPipeline channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }


    /**
     * 设置Boss线程数
     *
     * @param threadNum
     * @return
     */
    public AioServerStarter bossThreadNum(int threadNum) {
        this.bossThreadNum = threadNum;
        return this;
    }

    /**
     * 启动AIO服务
     *
     * @throws IOException
     */
    public void start() throws Exception {
        //打印框架信息
        LOGGER.info("\r\n" + AioServerConfig.BANNER + "\r\n  getty version:(" + AioServerConfig.VERSION + ")");

        if (channelInitializer == null) {
            throw new RuntimeException("ChannelPipeline can't be null");
        }
        start0(channel -> new AioChannel(channel, config, aioReadCompletionHandler, aioWriteCompletionHandler, chunkPool.allocateChunk(), channelInitializer));
    }

    /**
     * 内部启动
     *
     * @throws IOException
     */
    private final void start0(Function<AsynchronousSocketChannel, AioChannel> aioChannelFunction) throws IOException {
        try {

            //初始化worker线程池
            workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);

            //实例化读写回调
            aioReadCompletionHandler = new ReadCompletionHandler(workerThreadPool, new Semaphore(bossShareToWorkerThreadNum));
            aioWriteCompletionHandler = new WriteCompletionHandler();

            //实例化内存池
            this.chunkPool = new ChunkPool(config.getServerChunkSize(), bossThreadNum + workerThreadNum, config.isDirect());

            //函数式方法
            this.aioChannelFunction = aioChannelFunction;

            //IO线程分组
            asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(bossThreadNum, Thread::new);

            //打开服务通道
            this.serverSocketChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
            //设置socket参数
            if (config.getSocketOptions() != null) {
                for (Map.Entry<SocketOption<Object>, Object> entry : config.getSocketOptions().entrySet()) {
                    this.serverSocketChannel.setOption(entry.getKey(), entry.getValue());
                }
            }

            //绑定端口
            if (config.getHost() != null) {
                //服务端socket处理客户端socket连接是需要一定时间的。ServerSocket有一个队列，存放还没有来得及处理的客户端Socket，这个队列的容量就是backlog的含义。
                // 如果队列已经被客户端socket占满了，如果还有新的连接过来，那么ServerSocket会拒绝新的连接。
                // 也就是说backlog提供了容量限制功能，避免太多的客户端socket占用太多服务器资源
                serverSocketChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()), 1000);
            } else {
                serverSocketChannel.bind(new InetSocketAddress(config.getPort()), 1000);
            }

            //开启线程，开始接收客户端的连接
            new Thread(() -> {
                //循环监听客户端的连接
                while (running) {
                    //调用该方法返回的Future对象的get()方法
                    Future<AsynchronousSocketChannel> future = serverSocketChannel.accept();
                    try {
                        //get方法会阻塞该线程，直到有客户端连接过来，有点类似MQ，所以这种方式是阻塞式的异步IO
                        final AsynchronousSocketChannel channel = future.get();

                        //通过线程池创建客户端连接通道
                        workerThreadPool.execute(() -> {
                            //开始创建客户端会话
                            createChannel(channel);
                        });
                    } catch (Exception e) {
                        LOGGER.error("AsynchronousSocketChannel accept Exception", e);
                    }
                }
            }).start();

        } catch (IOException e) {
            shutdown();
            throw e;
        }
        LOGGER.info("getty server started on port {},bossThreadNum:{} ,workerThreadNum:{}", config.getPort(), bossThreadNum, workerThreadNum);
        LOGGER.info("getty server config is {}", config.toString());
    }

    /**
     * 为每个新连接创建AioChannel对象
     *
     * @param channel
     */
    private void createChannel(AsynchronousSocketChannel channel) {
        AioChannel aioChannel = null;
        try {
            aioChannel = aioChannelFunction.apply(channel);
            //创建成功立即开始读
            aioChannel.starRead();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (aioChannel != null) {
                closeChannel(channel);
            }
        }
    }

    /**
     * 关闭客户端连接通道
     *
     * @param channel
     */
    private void closeChannel(AsynchronousSocketChannel channel) {
        try {
            channel.shutdownInput();
        } catch (IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止服务
     */
    public final void shutdown() {
        //接收线程标志置为false
        running = false;
        try {
            if (serverSocketChannel != null) {
                serverSocketChannel.close();
                serverSocketChannel = null;
            }

            if (!workerThreadPool.isTerminated()) {
                workerThreadPool.shutdownNow();
            }

            if (!asynchronousChannelGroup.isTerminated()) {
                asynchronousChannelGroup.shutdownNow();
            }
            //该方法必须在shutdown或shutdownNow后执行,才会生效。否则会造成死锁
            //大概意思是这样的：该方法调用会被阻塞，并且在以下几种情况任意一种发生时都会导致该方法的执行:
            // 即shutdown方法被调用之后，或者参数中定义的timeout时间到达或者当前线程被打断，这几种情况任意一个发生了都会导致该方法在所有任务完成之后才执行。
            asynchronousChannelGroup.awaitTermination(5, TimeUnit.SECONDS);

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.error("server shutdown exception", e);
        }
    }


    /**
     * 设置Socket的TCP参数配置。
     * AIO客户端的有效可选范围为：<br/>
     * 2. StandardSocketOptions.SO_RCVBUF<br/>
     * 4. StandardSocketOptions.SO_REUSEADDR<br/>
     *
     * @param socketOption 配置项
     * @param value        配置值
     * @return
     */
    public final <V> AioServerStarter setOption(SocketOption<V> socketOption, V value) {
        config.setOption(socketOption, value);
        return this;
    }


}
