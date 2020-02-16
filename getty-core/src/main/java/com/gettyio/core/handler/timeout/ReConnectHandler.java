package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.util.concurrent.TimeUnit;


public class ReConnectHandler extends ChannelInboundHandlerAdapter implements TimerTask {

    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(ReConnectHandler.class);

    private int attempts = 0;// 时间基数，重连时间会越来越长
    private final HashedWheelTimer timer = new HashedWheelTimer();// 创建一个定时器

    private AioClientStarter clientStarter;

    public ReConnectHandler( AioClientStarter clientStarter) {
        this.clientStarter = clientStarter;
    }

    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {
        //重置时间基数
        attempts = 0;
        //停止定时器
        //timer.stop();
        super.channelAdded(aioChannel);
    }


    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {
        reConnect(aioChannel);
        super.channelClosed(aioChannel);
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        logger.debug("正在重连...");
        clientStarter.start();
    }


    //重连
    public void reConnect(AioChannel aioChannel) {
        //判断是否已经连接
        if (aioChannel.isInvalid()) {
            if (attempts < 10) {
                attempts++;
            }
            // 重连的间隔时间会越来越长
            int timeout = attempts * 1000;
            //启动定时器，通过定时器连接
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
        }
    }
}
