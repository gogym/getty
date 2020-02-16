package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.util.concurrent.TimeUnit;


public class ReConnectHandler extends ChannelInboundHandlerAdapter implements TimerTask {

    private int attempts;// 时间基数，重连时间会越来越长
    private final HashedWheelTimer timer = new HashedWheelTimer();// 创建一个定时器

    @Override
    public void channelAdded(AioChannel aioChannel) throws Exception {


        super.channelAdded(aioChannel);
    }


    @Override
    public void channelClosed(AioChannel aioChannel) throws Exception {


        super.channelClosed(aioChannel);
    }

    @Override
    public void run(Timeout timeout) throws Exception {


        

    }


    //重连
    public void reConnect(AioChannel aioChannel) {
        //判断是否已经连接
        if (aioChannel.isInvalid()) {
            attempts = 0;
            //启动定时器，通过定时器连接
            timer.newTimeout(this, 1000, TimeUnit.MILLISECONDS);
        }
    }
}
