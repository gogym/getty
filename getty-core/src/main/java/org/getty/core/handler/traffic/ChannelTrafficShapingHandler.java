/**
 * 包名：org.getty.core.handler.traffic
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.traffic;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.ChannelInOutBoundHandlerAdapter;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.util.ThreadPool;

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


    private long totalReadNum;
    private long totolWriteNum;

    //线程池
    ThreadPool pool;

    public ChannelTrafficShapingHandler(int checkInterval) {

        pool = new ThreadPool(ThreadPool.SingleThread, 1);
        pool.scheduleWithFixedRate(() -> {
            intervalTotalRead = intervalTotalReadTmp;
            intervalTotalReadTmp = 0;
            intervalTotalWrite = intervalTotalWriteTmp;
            intervalTotalWriteTmp = 0;
            System.out.println(":" + totalReadNum);
        }, 0, checkInterval, TimeUnit.MILLISECONDS);
    }


    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        switch (channelStateEnum) {
            case CHANNEL_READ:
                totalRead += bytes.length;
                intervalTotalReadTmp += bytes.length;
                totalReadNum++;
                break;
            case CHANNEL_WRITE:
                totalWrite += bytes.length;
                intervalTotalWriteTmp += bytes.length;
                totolWriteNum++;
                break;
            case CHANNEL_CLOSED:
                pool.shutdown();
                break;
            default:
                break;
        }
        super.handler(channelStateEnum, bytes, cause, aioChannel, pipelineDirection);
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
}
