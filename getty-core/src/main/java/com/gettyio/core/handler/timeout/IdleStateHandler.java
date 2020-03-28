/**
 * 包名：org.getty.core.handler.timeout
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.ThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * 类名：IdleStateHandler.java
 * 描述：起搏器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class IdleStateHandler extends ChannelAllBoundHandlerAdapter {


    boolean readerIdle = false;
    boolean writerIdle = false;

    /**
     * 线程池
     */
    ThreadPool pool;

    public IdleStateHandler(SocketChannel socketChannel, int readerIdleTimeSeconds, int writerIdleTimeSeconds) {
        this(socketChannel, readerIdleTimeSeconds, writerIdleTimeSeconds, TimeUnit.SECONDS);
    }

    public IdleStateHandler(final SocketChannel socketChannel, long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        pool = new ThreadPool(ThreadPool.FixedThread, 3);
        if (readerIdleTime > 0) {
            pool.scheduleWithFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (readerIdle) {
                        try {
                            IdleStateHandler.this.userEventTriggered(socketChannel, IdleState.READER_IDLE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    readerIdle = true;
                }
            }, 0, readerIdleTime, unit);
        }

        if (writerIdleTime > 0) {
            pool.scheduleWithFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (writerIdle) {
                        try {
                            IdleStateHandler.this.userEventTriggered(socketChannel, IdleState.WRITER_IDLE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    writerIdle = true;
                }
            }, 0, writerIdleTime, unit);
        }
    }


    @Override
    public void channelRead(SocketChannel socketChannel, Object obj) throws Exception {
        readerIdle = false;
        super.channelRead(socketChannel, obj);
    }

    @Override
    public void channelWrite(SocketChannel socketChannel, Object obj) throws Exception {
        writerIdle = false;
        super.channelWrite(socketChannel, obj);
    }

    @Override
    public void channelClosed(SocketChannel socketChannel) throws Exception {
        pool.shutdown();
        super.channelClosed(socketChannel);
    }

}
