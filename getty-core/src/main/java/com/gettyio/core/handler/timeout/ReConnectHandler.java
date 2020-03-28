package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.ThreadPool;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


public class ReConnectHandler extends ChannelInboundHandlerAdapter implements TimerTask {

    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(ReConnectHandler.class);

    private int attempts = 0;// 时间基数，重连时间会越来越长
    private long threshold = 1000;//间隔阈值
    private final HashedWheelTimer timer = new HashedWheelTimer();// 创建一个定时器

    private SocketChannel channel;

    public ReConnectHandler(SocketChannel socketChannel) {
        this.channel = socketChannel;
    }

    public ReConnectHandler(SocketChannel socketChannel, int threshold) {
        this.channel = socketChannel;
        this.threshold = threshold;
    }

    @Override
    public void channelAdded(SocketChannel aioChannel) throws Exception {
        //重置时间基数
        attempts = 0;
        super.channelAdded(aioChannel);
    }


    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        reConnect(socketChannel);
        super.channelClosed(socketChannel);
    }


    @Override
    public void exceptionCaught(SocketChannel socketChannel, Throwable cause) throws Exception {
        reConnect(socketChannel);
        super.exceptionCaught(socketChannel, cause);
    }

    @Override
    public void run(Timeout timeout) throws Exception {

        final BaseConfig aioClientConfig = channel.getConfig();
        final ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);

        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(AsynchronousChannelGroup.withFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable target) {
                return new Thread(target);
            }
        }));
        if (aioClientConfig.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : aioClientConfig.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        /**
         * 非阻塞连接
         */
        final AsynchronousSocketChannel finalSocketChannel = socketChannel;
        socketChannel.connect(new InetSocketAddress(aioClientConfig.getHost(), aioClientConfig.getPort()), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                logger.info("connect aio server success");
                //连接成功则构造AIOSession对象
                channel = new AioChannel(finalSocketChannel, aioClientConfig, new ReadCompletionHandler(workerThreadPool), new WriteCompletionHandler(), channel.getChunkPool(), channel.getChannelPipeline());
                channel.starRead();
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                logger.error("connect aio server  error", exc);
                reConnect(channel);
            }
        });


    }


    //重连
    public void reConnect(SocketChannel socketChannel) {
        //判断是否已经连接
        if (socketChannel.isInvalid()) {
            logger.debug("reconnect...");
            // 重连的间隔时间会越来越长
            long timeout = attempts * threshold;
            //启动定时器，通过定时器连接
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
            if (attempts < 10) {
                attempts++;
            }
        }
    }
}
