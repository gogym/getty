/**
 * 包名：org.getty.core.handler.timeout
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 类名：IdleStateHandler.java
 * 描述：起搏器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class IdleStateHandler extends ChannelInOutBoundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleStateHandler.class);

    boolean readerIdle = false;
    boolean writerIdle = false;

    /**
     * 线程池
     */
    ThreadPool pool;

    public IdleStateHandler(AioChannel aioChannel, int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        this(aioChannel, readerIdleTimeSeconds, writerIdleTimeSeconds, TimeUnit.SECONDS);
    }

    public IdleStateHandler(AioChannel aioChannel, long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        if (!(aioChannel instanceof TcpChannel)) {
            return;
        }
        pool = new ThreadPool(ThreadPool.FixedThread, 3);
        if (readerIdleTime > 0) {
            pool.scheduleWithFixedRate(() -> {
                if (readerIdle) {
                    userEventTriggered(aioChannel, IdleState.READER_IDLE);
                }
                readerIdle = true;
            }, 0, readerIdleTime, unit);
        }

        if (writerIdleTime > 0) {
            pool.scheduleWithFixedRate(() -> {
                if (writerIdle) {
                    userEventTriggered(aioChannel, IdleState.WRITER_IDLE);
                }
                writerIdle = true;
            }, 0, writerIdleTime, unit);
        }
    }


    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (aioChannel instanceof TcpChannel) {
            switch (channelStateEnum) {
                case CHANNEL_READ:
                    readerIdle = false;
                    break;
                case CHANNEL_WRITE:
                    writerIdle = false;
                    break;
                case CHANNEL_CLOSED:
                    pool.shutdown();
                    break;
                default:
                    break;
            }
        }
        super.handler(channelStateEnum, obj, aioChannel, pipelineDirection);
    }


}
