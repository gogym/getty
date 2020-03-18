package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.channel.config.AioConfig;
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
import java.util.concurrent.TimeUnit;


public class ReConnectHandler extends ChannelInboundHandlerAdapter implements TimerTask {

    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(ReConnectHandler.class);

    private int attempts = 0;// 时间基数，重连时间会越来越长
    private long threshold = 1000;//间隔阈值
    private final HashedWheelTimer timer = new HashedWheelTimer();// 创建一个定时器

    private AioChannel aioChannel;

    public ReConnectHandler(AioChannel aioChannel) {
        this.aioChannel = aioChannel;
    }

    public ReConnectHandler(AioChannel aioChannel, int threshold) {
        this.aioChannel = aioChannel;
        this.threshold = threshold;
    }

    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {
        //重置时间基数
        attempts = 0;
        super.channelAdded(aioChannel);
    }


    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {
        reConnect(aioChannel);
        super.channelClosed(aioChannel);
    }


    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause) throws Exception {
        reConnect(aioChannel);
        super.exceptionCaught(aioChannel, cause);
    }

    @Override
    public void run(Timeout timeout) throws Exception {

        AioConfig aioClientConfig = aioChannel.getConfig();
        ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 1);

        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(AsynchronousChannelGroup.withFixedThreadPool(1, Thread::new));
        if (aioClientConfig.getSocketOptions() != null) {
            for (Map.Entry<SocketOption<Object>, Object> entry : aioClientConfig.getSocketOptions().entrySet()) {
                socketChannel.setOption(entry.getKey(), entry.getValue());
            }
        }

        /**
         * 非阻塞连接
         */
        AsynchronousSocketChannel finalSocketChannel = socketChannel;
        socketChannel.connect(new InetSocketAddress(aioClientConfig.getHost(), aioClientConfig.getPort()), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                logger.info("connect tcp server success");
                //连接成功则构造AIOSession对象
                aioChannel = new TcpChannel(finalSocketChannel, aioClientConfig, new ReadCompletionHandler(workerThreadPool), new WriteCompletionHandler(), aioChannel.getChunkPool(), aioChannel.getChannelPipeline());
                aioChannel.starRead();
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                logger.error("connect tcp server  error", exc);
                reConnect(aioChannel);
            }
        });


    }


    //重连
    public void reConnect(AioChannel aioChannel) {
        //判断是否已经连接
        if (aioChannel.isInvalid()) {
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
