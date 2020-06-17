/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.channel;


import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.handler.ssl.SslHandler;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.channel.group.ChannelFutureListener;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import com.gettyio.core.util.ConcurrentSafeMap;
import com.gettyio.core.util.LinkedNonReadBlockQueue;
import com.gettyio.core.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;


/**
 * SocketChannel.java
 *
 * @description:io通道
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public abstract class SocketChannel {
    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketChannel.class);
    /**
     * 已关闭
     */
    protected static final byte CHANNEL_STATUS_CLOSED = 1;
    /**
     * 已开启
     */
    protected static final byte CHANNEL_STATUS_ENABLED = 3;

    /**
     * 默认保持长连接
     */
    protected boolean keepAlive = true;

    /**
     * 是否调用close()方法关闭
     */
    protected boolean initiateClose = false;


    /**
     * 内存池
     */
    protected ChunkPool chunkPool;

    /**
     * 会话当前状态
     */
    protected byte status = CHANNEL_STATUS_ENABLED;

    /**
     * 配置
     */
    protected BaseConfig config;

    /**
     * 默认责任链对象
     */
    protected DefaultChannelPipeline defaultChannelPipeline;
    /**
     * 关闭监听
     */
    protected ChannelFutureListener channelFutureListener;

    /**
     * 用于保存以及decode的消息
     */
    private LinkedNonReadBlockQueue<Object> outList = new LinkedNonReadBlockQueue<>();

    /**
     * 用于方便设置随通道传播的属性
     */
    protected ConcurrentSafeMap<String, Object> channelAttribute = new ConcurrentSafeMap<>();

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
    public void starRead() {
    }


    /**
     * 立即关闭会话
     */
    public abstract void close();

    /**
     * 主动关闭，标记
     *
     * @param initiateClose
     */
    public abstract void close(boolean initiateClose);

//-------------------------------------------------------------------------------------------------


    /**
     * 写出数据，经过责任链
     *
     * @param obj 写入的数组
     */
    public abstract void writeAndFlush(Object obj);

    /**
     * 写到BufferWriter输出器，不经过责任链
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
     * @throws Exception 异常
     */
    public void readToPipeline(Object obj) throws Exception {
        invokePipeline(ChannelState.CHANNEL_READ, obj);
    }

    //------------------------------------------------------------------------------

    /**
     * 正向执行管道处理
     *
     * @param channelState 数据流向
     * @throws Exception 异常
     */
    protected void invokePipeline(ChannelState channelState) throws Exception {
        invokePipeline(channelState, null);
    }

    /**
     * 正向执行管道处理
     *
     * @param channelState 数据流向
     * @param obj          消息对象
     * @throws Exception 异常
     */
    protected void invokePipeline(ChannelState channelState, Object obj) throws Exception {
        if (defaultChannelPipeline == null) {
            return;
        }

        ChannelHandlerAdapter channelHandlerAdapter = defaultChannelPipeline.inPipeFirst();
        if (channelHandlerAdapter == null) {
            return;
        }

        switch (channelState) {
            case NEW_CHANNEL:
                channelHandlerAdapter.channelAdded(this);
                break;
            case CHANNEL_READ:
                channelHandlerAdapter.decode(this, obj, outList);
                break;
            case CHANNEL_CLOSED:
                channelHandlerAdapter.channelClosed(this);
                break;
            case INPUT_SHUTDOWN:
                channelHandlerAdapter.exceptionCaught(this, new RuntimeException("socket channel is shutdown"));
                break;
            default:
                break;
        }


    }


    /**
     * 反向执行管道
     *
     * @param channelState 数据流向
     * @param obj          消息对象
     * @throws Exception 异常
     */
    protected void reverseInvokePipeline(ChannelState channelState, Object obj) throws Exception {
        ChannelHandlerAdapter channelHandlerAdapter = defaultChannelPipeline.outPipeFirst();
        if (channelHandlerAdapter == null) {
            //如果没有对应的处理器，直接输出到wirter
            writeToChannel(obj);
            return;
        }

        if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            channelHandlerAdapter.channelWrite(this, obj);
            channelHandlerAdapter.encode(this, obj);
            return;
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            channelHandlerAdapter.channelWrite(this, obj);
            channelHandlerAdapter.encode(this, obj);
            return;
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
        return defaultChannelPipeline != null ? defaultChannelPipeline : (defaultChannelPipeline = new DefaultChannelPipeline(this));
    }

//--------------------------------------------------------------------------------------

    public AsynchronousSocketChannel getAsynchronousSocketChannel() {
        return null;
    }

    public java.nio.channels.SocketChannel getSocketChannel() {
        return null;
    }


    public ChunkPool getChunkPool() {
        return chunkPool;
    }


    public ChannelPipeline getChannelPipeline() {
        return null;
    }

    /**
     * 设置SSLHandler
     *
     * @return AioChannel
     */
    public void setSslHandler(SslHandler sslHandler) {
    }

    /**
     * 获取ssl服务
     *
     * @return com.gettyio.core.handler.ssl.SslService
     */
    public SslHandler getSslHandler() {
        return null;
    }


    public void setSslHandshakeCompletedListener(IHandshakeCompletedListener handshakeCompletedListener) {
    }


    public BaseConfig getConfig() {
        return this.config;
    }

    public void setChannelFutureListener(ChannelFutureListener channelFutureListener) {
        this.channelFutureListener = channelFutureListener;
    }


    public ConcurrentSafeMap<String, Object> getChannelAttribute() {
        return channelAttribute;
    }

    public Object getChannelAttribute(String key) {
        if (StringUtil.isEmpty(key)) {
            return null;
        }
        return channelAttribute.get(key);
    }


    public void setChannelAttribute(String key, Object obj) {
        this.channelAttribute.put(key, obj);
    }


    public void removeChannelAttribute(String key) {
        if (StringUtil.isEmpty(key)) {
            return;
        }
        this.channelAttribute.remove(key);
    }


    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public boolean isInitiateClose() {
        return initiateClose;
    }
}
