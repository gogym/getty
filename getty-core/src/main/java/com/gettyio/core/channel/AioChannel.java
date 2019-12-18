/**
 * 包名：org.getty.core.channel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel;


import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.config.AioConfig;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.channel.group.ChannelFutureListener;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

/**
 * 类名：AioChannel.java
 * 描述：AIO传输通道
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class AioChannel {
    protected static final Logger logger = LoggerFactory.getLogger(AioChannel.class);
    /**
     * 已关闭
     */
    protected static final byte CHANNEL_STATUS_CLOSED = 1;
    /**
     * 正常
     */
    protected static final byte CHANNEL_STATUS_ENABLED = 3;

    /**
     * 内存池
     */
    protected ChunkPool chunkPool;

    /**
     * 会话当前状态
     */
    protected byte status = CHANNEL_STATUS_ENABLED;

    //配置
    protected AioConfig aioConfig;

    /**
     * 责任链对象
     */
    protected DefaultChannelPipeline defaultChannelPipeline;
    /**
     * 关闭监听
     */
    protected ChannelFutureListener channelFutureListener;

    //-------------------------------------------------------------------------------------


    /**
     * 获取当前aioChannel的唯一标识
     *
     * @return String
     */
    public final String getChannelId() {
        return "aioChannel-" + System.identityHashCode(this);
    }

    /**
     * 当前会话是否已失效
     *
     * @return boolean
     */
    public final boolean isInvalid() {
        return status != CHANNEL_STATUS_ENABLED;
    }


    /**
     * 开始读取，很重要，只有调用该方法，才会开始监听消息读取
     */
    public abstract void starRead();


    /**
     * 立即关闭会话
     */
    public abstract void close();


//-------------------------------------------------------------------------------------------------


    /**
     * 写出数据
     *
     * @param obj 写入的数组
     */
    public abstract void writeAndFlush(Object obj);

    /**
     * 直接写到socket通道
     *
     * @param obj 写入的数组
     */
    public abstract void writeToChannel(Object obj);

    //-----------------------------------------------------------------------------------

    /**
     * 获取本地地址
     *
     * @return InetSocketAddress
     * @throws IOException 异常
     */
    public abstract InetSocketAddress getLocalAddress() throws IOException;

    /**
     * 获取远程地址
     *
     * @return InetSocketAddress
     * @throws IOException 异常
     */
    public InetSocketAddress getRemoteAddress() throws IOException {
        return null;
    }


    //------------------------------------------------------------------------------

    /**
     * 消息读取到责任链管道
     *
     * @param obj 消息对象
     */
    public void readToPipeline(Object obj) {
        invokePipeline(ChannelState.CHANNEL_READ, obj);
    }


    /**
     * 正向执行管道处理
     *
     * @param channelStateEnum 数据流向
     */
    protected void invokePipeline(ChannelState channelStateEnum) {
        invokePipeline(channelStateEnum, null);
    }

    /**
     * 正向执行管道处理
     *
     * @param channelStateEnum 数据流向
     * @param obj              消息对象
     */
    protected void invokePipeline(ChannelState channelStateEnum, Object obj) {

        Iterator<ChannelHandlerAdapter> iterator = defaultChannelPipeline.getIterator();
        while (iterator.hasNext()) {
            ChannelHandlerAdapter channelHandlerAdapter = iterator.next();
            if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                channelHandlerAdapter.handler(channelStateEnum, obj, this, PipelineDirection.IN);
                return;
            }
        }
    }


    /**
     * 反向执行管道
     *
     * @param channelStateEnum 数据流向
     * @param obj              消息对象
     */
    protected void reverseInvokePipeline(ChannelState channelStateEnum, Object obj) {

        Iterator<ChannelHandlerAdapter> iterator = defaultChannelPipeline.getReverseIterator();
        while (iterator.hasNext()) {
            ChannelHandlerAdapter channelHandlerAdapter = iterator.next();
            if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                channelHandlerAdapter.handler(channelStateEnum, obj, this, PipelineDirection.OUT);
                return;
            }
        }
        //如果没有对应的处理器，直接输出到wirter
        writeToChannel(obj);
    }


    /**
     * 获取默认的责任链
     *
     * @return com.gettyio.core.pipeline.DefaultChannelPipeline
     */
    public DefaultChannelPipeline getDefaultChannelPipeline() {
        return defaultChannelPipeline != null ? defaultChannelPipeline : (defaultChannelPipeline = new DefaultChannelPipeline());
    }

//--------------------------------------------------------------------------------------

    /**
     * 创建SSL
     *
     * @param sslService ssl服务
     * @return AioChannel
     */
    public AioChannel createSSL(SslService sslService) {
        return this;
    }

    /**
     * 获取ssl服务
     *
     * @return com.gettyio.core.handler.ssl.SslService
     */
    public SslService getSSLService() {
        return null;
    }

    public AioConfig getConfig() {
        return this.aioConfig;
    }

    public void setChannelFutureListener(ChannelFutureListener channelFutureListener) {
        this.channelFutureListener = channelFutureListener;
    }


}
