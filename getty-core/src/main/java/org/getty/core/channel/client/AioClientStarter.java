/**
 * 包名：org.getty.core.channel.client
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.client;

import org.getty.core.buffer.ChunkPool;
import org.getty.core.channel.AioChannel;
import org.getty.core.channel.internal.ReadCompletionHandler;
import org.getty.core.channel.internal.WriteCompletionHandler;
import org.getty.core.pipeline.ChannelPipeline;
import org.getty.core.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 类名：AioClientStarter.java
 * 描述：Aio客户端
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AioClientStarter {

    private static final Logger logger = LoggerFactory.getLogger(AioClientStarter.class);

    //客户端服务配置。
    private AioClientConfig aioClientConfig = new AioClientConfig();
    //aio通道
    private AioChannel aioChannel;
    //内存池
    private ChunkPool chunkPool = null;
    //线程池
    private ThreadPool workerThreadPool;
    //IO线程组。
    private AsynchronousChannelGroup asynchronousChannelGroup;
    //责任链对象
    protected ChannelPipeline channelInitializer;

    /**
     * 简单启动
     *
     * @param host 服务器地址
     * @param port 服务器端口号
     */
    public AioClientStarter(String host, int port) {
        aioClientConfig.setHost(host);
        aioClientConfig.setPort(port);
    }


    /**
     * 配置文件启动
     *
     * @return
     * @params [aioClientConfig]
     */
    public AioClientStarter(AioClientConfig aioClientConfig) {
        if (null == aioClientConfig.getHost() || "".equals(aioClientConfig.getHost())) {
            throw new NullPointerException("The connection host is null.");
        }
        if (0 == aioClientConfig.getPort()) {
            throw new NullPointerException("The connection port is null.");
        }
        this.aioClientConfig = aioClientConfig;
    }


    /**
     * 设置责任链
     *
     * @param channelInitializer
     * @return
     */
    public AioClientStarter channelInitializer(ChannelPipeline channelInitializer) {
        this.channelInitializer = channelInitializer;
        return this;
    }

    /**
     * 启动客户端。
     */
    public final void start() throws Exception {

        if (this.channelInitializer == null) {
            throw new NullPointerException("The ChannelPipeline is null.");
        }
        //初始化worker线程池
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);
        this.asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(1, Thread::new);
        //调用内部启动
        start0(asynchronousChannelGroup);
    }


    /**
     * 该方法为非阻塞连接。连接成功与否，会回调
     *
     * @param asynchronousChannelGroup
     */
    private void start0(AsynchronousChannelGroup asynchronousChannelGroup) throws Exception {

        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(asynchronousChannelGroup);

        if (chunkPool == null) {
            chunkPool = new ChunkPool(aioClientConfig.getClientChunkSize(), 1, aioClientConfig.isDirect());
        }

        if (aioClientConfig.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : aioClientConfig.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        /**
         * 非阻塞连接
         */
        socketChannel.connect(new InetSocketAddress(aioClientConfig.getHost(), aioClientConfig.getPort()), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                logger.info("server connect success");
                //连接成功则构造AIOSession对象
                aioChannel = new AioChannel(socketChannel, aioClientConfig, new ReadCompletionHandler(workerThreadPool, new Semaphore(1)), new WriteCompletionHandler(), chunkPool.allocateChunk(), channelInitializer);
                aioChannel.starRead();
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                logger.error("server connect error", exc);
            }
        });
    }


    /**
     * 停止客户端
     */
    public final void shutdown() {
        showdown0(false);
    }


    private void showdown0(boolean flag) {
        if (aioChannel != null) {
            aioChannel.close();
            aioChannel = null;
        }
        //仅Client内部创建的ChannelGroup需要shutdown
        if (asynchronousChannelGroup != null) {
            asynchronousChannelGroup.shutdown();
            asynchronousChannelGroup = null;
        }
    }

    /**
     * 设置Socket的TCP参数配置
     * AIO客户端的可选为：
     * //套接字发送缓冲区的大小。int
     * 1. StandardSocketOptions.SO_SNDBUF<br/>
     * //套接字接收缓冲区的大小。int
     * 2. StandardSocketOptions.SO_RCVBUF<br/>
     * //使连接保持活动状态。boolean
     * 3. StandardSocketOptions.SO_KEEPALIVE<br/>
     * //重用地址。boolean
     * 4. StandardSocketOptions.SO_REUSEADDR<br/>
     * //禁用Nagle算法。boolean
     * 5. StandardSocketOptions.TCP_NODELAY
     *
     * @param socketOption 配置项
     * @param value        配置值
     * @return
     */
    public final <V> AioClientStarter setOption(SocketOption<V> socketOption, V value) {
        aioClientConfig.setOption(socketOption, value);
        return this;
    }


    /**
     * 获取AioChannel
     *
     * @return
     */
    public AioChannel getAioChannel() {
        if (aioChannel != null) {
            if (aioChannel.getSSLService() != null) {
                //如果开启了ssl,要先判断是否已经完成握手
                if (aioChannel.getSSLService().getSsl().isHandshakeCompleted()) {
                    return aioChannel;
                }
                throw new RuntimeException("The SSL handshcke is not yet complete");
            }
            return aioChannel;
        }
        throw new NullPointerException("AioChannel was null");
    }
}
