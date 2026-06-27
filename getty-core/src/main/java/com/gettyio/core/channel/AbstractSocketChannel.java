/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.channel;

import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.channel.group.ChannelFutureListener;
import com.gettyio.core.handler.ssl.IHandshakeListener;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.util.ConcurrentSafeMap;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * I/O 通道的抽象基类。
 * <p>
 * 定义了所有通道（AIO / NIO / UDP）共有的状态管理、管道执行、配置访问等核心行为。
 * 子类只需实现具体的读写和关闭逻辑。
 * </p>
 *
 * @author gogym
 */
public abstract class AbstractSocketChannel {

    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractSocketChannel.class);

    // ==================== 通道状态常量 ====================

    /** 通道已关闭 */
    protected static final byte CHANNEL_STATUS_CLOSED = 1;

    /** 通道已启用（正常工作） */
    protected static final byte CHANNEL_STATUS_ENABLED = 3;

    // ==================== 通道状态字段 ====================

    /**
     * 通道当前状态。使用 volatile 保证多线程间的可见性。
     */
    protected volatile byte status = CHANNEL_STATUS_ENABLED;

    /** 是否保持长连接（短连接在写完后自动关闭） */
    protected volatile boolean keepAlive = true;

    /** 是否由本端主动调用 close() 关闭 */
    protected volatile boolean initiateClose;

    /** SSL 握手是否已完成 */
    protected volatile boolean handShake;

    /** 当前通道是否可写入（流控标志） */
    protected volatile boolean writeable = true;

    // ==================== 核心组件 ====================

    /** 内存池 */
    protected ByteBufferPool byteBufferPool;

    /** 通道配置 */
    protected BaseConfig config;

    /** 管道（责任链），惰性初始化 */
    protected ChannelPipeline channelPipeline;

    /** 通道关闭监听器 */
    protected ChannelFutureListener channelFutureListener;

    /** 通道属性（读写安全的 Map） */
    protected ConcurrentSafeMap<String, Object> channelAttribute = new ConcurrentSafeMap<>();

    /** 管道初始化器 */
    protected ChannelInitializer channelInitializer;

    // ==================== 标识与状态查询 ====================

    /**
     * 获取通道唯一标识。
     *
     * @return 通道 ID 字符串
     */
    public final String getChannelId() {
        return "channel-" + System.identityHashCode(this);
    }

    /**
     * 通道是否已失效（非启用状态）。
     *
     * @return true 表示通道已关闭
     */
    public final boolean isInvalid() {
        return status != CHANNEL_STATUS_ENABLED;
    }

    // ==================== 抽象方法（子类必须实现） ====================

    /**
     * 开始读取。调用此方法后，通道才会开始监听消息读取。
     */
    public abstract void starRead();

    /**
     * 立即关闭通道，释放所有资源。
     */
    public abstract void close();

    /**
     * 带主动关闭标志的关闭方法。
     *
     * @param initiateClose true 表示本端主动关闭
     */
    public abstract void close(boolean initiateClose);

    /**
     * 写出数据，经过责任链处理。
     *
     * @param obj 待写出的数据
     * @return true 表示提交成功，false 表示被流控拒绝
     */
    public abstract boolean writeAndFlush(Object obj);

    /**
     * 经过责任链编码后追加到写缓冲区链表，不触发实际写出。
     * <p>
     * 数据经管道编码后到达 {@link #writeToSocket(PooledByteBuffer)}，
     * 仅追加到 BufferWriter 链表，需配合 {@link #flush()} 使用才能实际发出。
     * </p>
     *
     * @param obj 待写出的数据
     */
    public abstract void write(Object obj);

    /**
     * 刷新写缓冲区，触发实际写出。
     * <p>
     * 将 {@link #write(Object)} 或 {@link #writeAndFlush(Object)} 累积的数据
     * 从链表取出并提交给底层通道写出。
     * </p>
     */
    public abstract void flush();

    /**
     * 直接写到输出器，跳过责任链。仅追加到 BufferWriter 链表。
     *
     * @param obj 待写出的池化缓冲区
     * @throws IOException 写出失败时抛出
     */
    public abstract void writeToSocket(PooledByteBuffer obj) throws IOException;

    /**
     * 直接写出 byte[] 数据，跳过责任链。
     * <p>
     * 用于方案二（编码器直出 byte[]）场景，避免编码器分配 PooledByteBuffer。
     * PooledByteBuffer 由写线程分配，确保分配和释放在同一线程，消除跨线程回收问题。
     * </p>
     *
     * @param bytes 待写出的字节数组
     * @throws IOException 写出失败时抛出
     */
    public abstract void writeToSocket(byte[] bytes) throws IOException;

    /**
     * 获取本地地址。
     *
     * @return 本地 Socket 地址
     * @throws IOException 通道已关闭时抛出
     */
    public abstract InetSocketAddress getLocalAddress() throws IOException;

    /**
     * 获取远程地址。
     *
     * @return 远程 Socket 地址
     * @throws IOException 通道已关闭时抛出
     */
    public abstract InetSocketAddress getRemoteAddress() throws IOException;

    // ==================== 管道执行 ====================

    /**
     * 正向执行管道处理。
     *
     * @param channelState 数据流向（读 / 写 / 事件等）
     * @param obj          消息对象，可为 null
     * @throws Exception 处理过程中的异常
     */
    protected void invokePipeline(ChannelState channelState, Object obj) throws Exception {
        ChannelHandlerContext ctx = getChannelPipeline().head();
        ctx.fireChannelProcess(channelState, obj);
    }

    /**
     * 反向执行管道处理（用于写出方向）。
     *
     * @param channelState 数据流向
     * @param obj          消息对象
     * @throws Exception 处理过程中的异常
     */
    protected void reverseInvokePipeline(ChannelState channelState, Object obj) throws Exception {
        ChannelHandlerContext ctx = getChannelPipeline().tail();
        ctx.fireChannelProcess(channelState, obj);
    }

    /**
     * 获取责任链管道（惰性初始化）。
     *
     * @return ChannelPipeline
     */
    public ChannelPipeline getChannelPipeline() {
        ChannelPipeline p = channelPipeline;
        if (p == null) {
            p = new DefaultChannelPipeline(this);
            channelPipeline = p;
        }
        return p;
    }

    // ==================== 内存池 ====================

    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }

    // ==================== SSL 相关（默认空实现，子类按需覆盖） ====================

    /**
     * 设置 SSL 处理器。默认空实现，TCP 通道子类需覆盖。
     */
    public void setSslHandler(SSLHandler sslHandler) {
    }

    /**
     * 获取 SSL 处理器。
     *
     * @return SSLHandler，未设置时返回 null
     */
    public SSLHandler getSslHandler() {
        return null;
    }

    /**
     * 获取 SSL 握手监听器。
     *
     * @return IHandshakeListener，未设置时返回 null
     */
    public IHandshakeListener getSslHandshakeListener() {
        return null;
    }

    /**
     * 设置 SSL 握手监听器。默认空实现，TCP 通道子类需覆盖。
     */
    public void setSslHandshakeListener(IHandshakeListener handshakeListener) {
    }

    // ==================== 配置与属性 ====================

    public BaseConfig getConfig() {
        return config;
    }

    public void setChannelFutureListener(ChannelFutureListener channelFutureListener) {
        this.channelFutureListener = channelFutureListener;
    }

    public ConcurrentSafeMap<String, Object> getChannelAttribute() {
        return channelAttribute;
    }

    public Object getChannelAttribute(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return channelAttribute.get(key);
    }

    public void setChannelAttribute(String key, Object obj) {
        channelAttribute.put(key, obj);
    }

    public void removeChannelAttribute(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        channelAttribute.remove(key);
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

    public boolean isHandShake() {
        return handShake;
    }

    public void setHandShake(boolean handShake) {
        this.handShake = handShake;
    }

    public boolean isWriteable() {
        return writeable;
    }

    public ChannelInitializer getChannelInitializer() {
        return channelInitializer;
    }
}
