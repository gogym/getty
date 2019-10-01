/**
 * 包名：org.getty.core.handler.timeout
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.timeout;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.ChannelInOutBoundHandlerAdapter;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.util.ThreadPool;
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
    public void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection) {
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
        super.handler(channelStateEnum, bytes, cause, aioChannel, pipelineDirection);
    }


}
