/**
 * 包名：org.getty.core.handler.traffic
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.traffic;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.util.ThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * 类名：ChannelTrafficShapingHandler.java
 * 描述：通道级别统计
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class ChannelTrafficShapingHandler extends ChannelInOutBoundHandlerAdapter {

    //总读取字节
    private long totalRead;
    //总写出字节
    private long totalWrite;
    //时间间隔内的吞吐量
    private long intervalTotalRead;
    private long intervalTotalWrite;
    long intervalTotalReadTmp = 0;
    long intervalTotalWriteTmp = 0;

    //读写次数
    private long totalReadCount;
    private long totolWriteCount;

    //线程池
    ThreadPool pool;

    public ChannelTrafficShapingHandler(int checkInterval) {

        pool = new ThreadPool(ThreadPool.SingleThread, 1);
        pool.scheduleWithFixedRate(() -> {
            intervalTotalRead = intervalTotalReadTmp;
            intervalTotalReadTmp = 0;
            intervalTotalWrite = intervalTotalWriteTmp;
            intervalTotalWriteTmp = 0;
        }, 0, checkInterval, TimeUnit.MILLISECONDS);
    }


    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        if (aioChannel instanceof TcpChannel) {
            byte[] bytes = (byte[]) obj;
            switch (channelStateEnum) {
                case CHANNEL_READ:
                    totalRead += bytes.length;
                    intervalTotalReadTmp += bytes.length;
                    totalReadCount++;
                    break;
                case CHANNEL_WRITE:
                    totalWrite += bytes.length;
                    intervalTotalWriteTmp += bytes.length;
                    totolWriteCount++;
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


    public long getTotalRead() {
        return totalRead;
    }

    public long getTotalWrite() {
        return totalWrite;
    }

    public long getIntervalTotalRead() {
        return intervalTotalRead;
    }

    public long getIntervalTotalWrite() {
        return intervalTotalWrite;
    }

    public long getTotalReadCount() {
        return totalReadCount;
    }

    public long getTotolWriteCount() {
        return totolWriteCount;
    }


}
