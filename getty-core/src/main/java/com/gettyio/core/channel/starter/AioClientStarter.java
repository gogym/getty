/**
 * 包名：org.getty.core.channel.client
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.starter;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.buffer.Time;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.UdpChannel;
import com.gettyio.core.channel.config.ClientConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.*;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * 类名：AioClientStarter.java
 * 描述：Aio客户端
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AioClientStarter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AioClientStarter.class);

    //客户端服务配置。
    private ClientConfig clientConfig = new ClientConfig();
    //aio通道
    private SocketChannel aioChannel;
    //内存池
    private ChunkPool chunkPool;
    //线程池
    private ThreadPool workerThreadPool;
    //IO线程组。
    private AsynchronousChannelGroup asynchronousChannelGroup;
    //责任链对象
    protected ChannelPipeline channelPipeline;

    /**
     * 简单启动
     *
     * @param host 服务器地址
     * @param port 服务器端口号
     */
    public AioClientStarter(String host, int port) {
        clientConfig.setHost(host);
        clientConfig.setPort(port);
    }


    /**
     * 配置文件启动
     *
     * @param clientConfig 配置
     */
    public AioClientStarter(ClientConfig clientConfig) {
        if (null == clientConfig.getHost() || "".equals(clientConfig.getHost())) {
            throw new NullPointerException("The connection host is null.");
        }
        if (0 == clientConfig.getPort()) {
            throw new NullPointerException("The connection port is null.");
        }
        this.clientConfig = clientConfig;
    }


    /**
     * 设置责任链
     *
     * @param channelPipeline 责任链
     * @return AioClientStarter
     */
    public AioClientStarter channelInitializer(ChannelPipeline channelPipeline) {
        this.channelPipeline = channelPipeline;
        return this;
    }


    /**
     * 启动客户端。
     *
     * @throws Exception 异常
     */
    public final void start() throws Exception {

        if (this.channelPipeline == null) {
            throw new NullPointerException("The ChannelPipeline is null.");
        }
        //初始化worker线程池
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);
        //初始化内存池
        chunkPool = new ChunkPool(clientConfig.getClientChunkSize(), new Time(), clientConfig.isDirect());
        this.asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable target) {
                return new Thread(target);
            }
        });
        //调用内部启动
        startTcp(asynchronousChannelGroup);

    }


    /**
     * 该方法为非阻塞连接。连接成功与否，会回调
     *
     * @param asynchronousChannelGroup 线程组
     */
    private void startTcp(AsynchronousChannelGroup asynchronousChannelGroup) throws Exception {

        final AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(asynchronousChannelGroup);
        if (clientConfig.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : clientConfig.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }
        /**
         * 非阻塞连接
         */
        socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                logger.info("connect aio server success");
                //连接成功则构造AIOSession对象
                aioChannel = new AioChannel(socketChannel, clientConfig, new ReadCompletionHandler(workerThreadPool), new WriteCompletionHandler(), chunkPool, channelPipeline);
                aioChannel.starRead();
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                logger.error("connect aio server  error", exc);
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
     * 获取AioChannel
     *
     * @return AioChannel
     */
    public SocketChannel getAioChannel() {
        if (aioChannel != null) {
            if (aioChannel.getSslHandler() != null) {
                //如果开启了ssl,要先判断是否已经完成握手
                if (aioChannel.getSslHandler().getSslService().getSsl().isHandshakeCompleted()) {
                    return aioChannel;
                }
                aioChannel.close();
                throw new RuntimeException("The SSL handshcke is not yet complete");
            }
            return aioChannel;
        }
        throw new NullPointerException("AioChannel was null");
    }
}
