/**
 * 包名：org.getty.core.channel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel;


import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.internal.ReadCompletionHandler;
import com.gettyio.core.channel.internal.WriteCompletionHandler;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.pipeline.PipelineDirection;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;
import com.gettyio.core.channel.group.ChannelFutureListener;
import com.gettyio.core.function.Function;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.all.ChannelInOutBoundHandlerAdapter;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 类名：AioChannel.java
 * 描述：AIO传输通道
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AioChannel implements Function<BufferWriter, Void> {
    private static final Logger logger = LoggerFactory.getLogger(AioChannel.class);
    /**
     * 已关闭
     */
    protected static final byte CHANNEL_STATUS_CLOSED = 1;
    /**
     * 正常
     */
    protected static final byte CHANNEL_STATUS_ENABLED = 3;

    /**
     * 通信channel对象
     */
    protected AsynchronousSocketChannel channel;

    /**
     * 内存池
     */
    protected ChunkPool chunkPool;

    /**
     * 读缓冲。
     */
    protected ByteBuffer readByteBuffer;
    /**
     * 写缓冲
     */
    protected ByteBuffer writeByteBuffer;
    /**
     * 会话当前状态
     */
    protected byte status = CHANNEL_STATUS_ENABLED;
    /**
     * 输出信号量
     */
    private Semaphore semaphore = new Semaphore(1);

    //读写回调
    private ReadCompletionHandler readCompletionHandler;
    private WriteCompletionHandler writeCompletionHandler;
    //配置
    private AioConfig aioConfig;
    private BufferWriter bufferWriter;

    /**
     * 责任链对象
     */
    private DefaultChannelPipeline defaultChannelPipeline;
    /**
     * 关闭监听
     */
    private ChannelFutureListener channelFutureListener;
    /**
     * SSL服务
     */
    private SslService sslService;

    /**
     * @param channel                通道
     * @param config                 配置
     * @param readCompletionHandler  读回调
     * @param writeCompletionHandler 写回调
     * @param chunkPool              内存池
     * @param channelPipeline        责任链
     */
    public AioChannel(AsynchronousSocketChannel channel, final AioConfig config, ReadCompletionHandler readCompletionHandler, WriteCompletionHandler writeCompletionHandler, ChunkPool chunkPool, ChannelPipeline channelPipeline) {
        this.channel = channel;
        this.readCompletionHandler = readCompletionHandler;
        this.writeCompletionHandler = writeCompletionHandler;
        this.aioConfig = config;
        this.chunkPool = chunkPool;
        try {
            //初始化读缓冲区
            this.readByteBuffer = chunkPool.allocate(config.getReadBufferSize(), 1000);
            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            throw new RuntimeException("channelPipeline init exception", e);
        }

        //写通道
        bufferWriter = new BufferWriter(chunkPool, this);

        //触发责任链回调
        invokePipeline(ChannelState.NEW_CHANNEL);
    }


    /**
     * 开始读取，很重要，只有调用该方法，才会开始监听消息读取
     */
    public void starRead() {
        continueRead();
        if (this.sslService != null) {
            //若开启了SSL，则需要握手
            this.sslService.beginHandshake();
        }
    }


    /**
     * 立即关闭会话
     */
    public synchronized void close() {
        if (status == CHANNEL_STATUS_CLOSED) {
            logger.warn("Channel:{} is closed:", getChannelId());
            return;
        }

        try {
            if (!bufferWriter.isClosed()) {
                bufferWriter.close();
            }
            bufferWriter = null;
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (readByteBuffer != null) {
            chunkPool.deallocate(readByteBuffer);
        }

        if (writeByteBuffer != null) {
            chunkPool.deallocate(writeByteBuffer);
        }

        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }

        try {
            channel.shutdownInput();
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        }
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        }
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("close channel exception", e);
        }
        //更新状态
        status = CHANNEL_STATUS_CLOSED;
        //触发责任链通知
        invokePipeline(ChannelState.CHANNEL_CLOSED);

        //最后需要清空责任链
        if (defaultChannelPipeline != null) {
            defaultChannelPipeline.clean();
            defaultChannelPipeline = null;
        }

    }

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


    //--------------------------------------------------------------------------

    /**
     * 读取socket通道内的数据
     */
    protected void continueRead() {
        if (status == CHANNEL_STATUS_CLOSED) {
            return;
        }
        readFromChannel0(readByteBuffer);
    }

    /**
     * 从通道socket中读取数据
     *
     * @param buffer 读取的缓冲区
     */
    protected final void readFromChannel0(ByteBuffer buffer) {
        channel.read(buffer, this, readCompletionHandler);
    }


    /**
     * socket通道的读回调操作
     *
     * @param eof 状态回调标记
     */
    public void readFromChannel(boolean eof) {

        final ByteBuffer readBuffer = this.readByteBuffer;
        //读取缓冲区数据到管道
        if (null != readBuffer) {
            readBuffer.flip();
            //读取缓冲区数据，输送到责任链
            while (readBuffer.hasRemaining()) {
                byte[] bytes = new byte[readBuffer.remaining()];
                readBuffer.get(bytes, 0, bytes.length);
                readToPipeline(bytes);
            }
            if (eof) {
                RuntimeException exception = new RuntimeException("socket channel is shutdown");
                logger.error(exception.getMessage(), exception);
                invokePipeline(ChannelState.INPUT_SHUTDOWN);
                close();
                return;
            }
            //触发读取完成，处理后续操作
            readCompleted(readBuffer);
        }
    }

    /**
     * socket读取完成
     *
     * @param readBuffer 读取的缓冲区
     */
    public void readCompleted(ByteBuffer readBuffer) {

        if (readBuffer == null) {
            return;
        }
        //数据读取完毕
        if (readBuffer.remaining() == 0) {
            //position = 0;limit = capacity;mark = -1;  有点初始化的味道，但是并不影响底层byte数组的内容
            readBuffer.clear();
        } else if (readBuffer.position() > 0) {
            //把从position到limit中的内容移到0到limit-position的区域内，position和limit的取值也分别变成limit-position、capacity。如果先将positon设置到limit，再compact，那么相当于clear()
            readBuffer.compact();
        } else {
            readBuffer.position(readBuffer.limit());
            readBuffer.limit(readBuffer.capacity());
        }
        //再次调用读取方法。循环监听socket通道数据的读取
        continueRead();
    }


    /**
     * 消息读取到责任链管道
     *
     * @param bytes 读取的数组
     */
    public void readToPipeline(byte[] bytes) {
        invokePipeline(ChannelState.CHANNEL_READ, bytes);
    }

//-------------------------------------------------------------------------------------------------

    /**
     * 写数据到责任链管道
     *
     * @param bytes 写入的数组
     */
    public void writeAndFlush(byte[] bytes) {
        reverseInvokePipeline(ChannelState.CHANNEL_WRITE, bytes);
    }

    /**
     * 直接写到socket通道
     *
     * @param bytes 写入的数组
     */
    public void writeToChannel(byte[] bytes) {
        writeAndFlush0(bytes);
    }


    /**
     * 写数据到wirter
     *
     * @param bytes 写入的数组
     */
    private void writeAndFlush0(byte[] bytes) {
        try {
            bufferWriter.writeAndFlush(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 写到socket通道
     *
     * @param buffer 写入的数组
     */
    protected final void writeToChannel0(ByteBuffer buffer) {
        channel.write(buffer, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
        //channel.write(buffer);
        //chunkPool.deallocate(buffer);
    }

    /**
     * 继续写
     *
     * @param writeBuffer 写入的缓冲区
     */
    private void continueWrite(ByteBuffer writeBuffer) {
        writeToChannel0(writeBuffer);
    }


    /**
     * 写操作完成回调
     * 需要同步控制
     */
    public void writeCompleted() {
        if (writeByteBuffer != null) {
            chunkPool.deallocate(writeByteBuffer);
        }
    }

    //-----------------------------------------------------------------------------------

    /**
     * 获取本地地址
     *
     * @return InetSocketAddress
     * @throws IOException 异常
     */
    public final InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getLocalAddress();
    }

    /**
     * 获取远程地址
     *
     * @return InetSocketAddress
     * @throws IOException 异常
     */
    public final InetSocketAddress getRemoteAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    /**
     * 断言
     *
     * @throws IOException 异常
     */
    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || channel == null) {
            throw new IOException("channel is closed");
        }
    }

    //------------------------------------------------------------------------------


    /**
     * 正向执行管道处理
     *
     * @param channelStateEnum 数据流向
     */
    private void invokePipeline(ChannelState channelStateEnum) {
        invokePipeline(channelStateEnum, null);
    }

    /**
     * 正向执行管道处理
     *
     * @param channelStateEnum 数据流向
     * @param bytes            数组
     */
    private void invokePipeline(ChannelState channelStateEnum, byte[] bytes) {

        Iterator<ChannelHandlerAdapter> iterator = defaultChannelPipeline.getIterator();
        while (iterator.hasNext()) {
            ChannelHandlerAdapter channelHandlerAdapter = iterator.next();
            if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                channelHandlerAdapter.handler(channelStateEnum, bytes, this, PipelineDirection.IN);
                return;
            }
        }
    }


    /**
     * 反向执行管道
     *
     * @param channelStateEnum 数据流向
     * @param bytes            数组
     */
    private void reverseInvokePipeline(ChannelState channelStateEnum, byte[] bytes) {

        Iterator<ChannelHandlerAdapter> iterator = defaultChannelPipeline.getReverseIterator();
        while (iterator.hasNext()) {
            ChannelHandlerAdapter channelHandlerAdapter = iterator.next();
            if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter || channelHandlerAdapter instanceof ChannelInOutBoundHandlerAdapter) {
                channelHandlerAdapter.handler(channelStateEnum, bytes, this, PipelineDirection.OUT);
                return;
            }
        }
        //如果没有对应的处理器，直接输出到wirter
        writeAndFlush0(bytes);
    }


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
        this.sslService = sslService;
        return this;
    }

    public SslService getSSLService() {
        return this.sslService;
    }

    public AioConfig getServerConfig() {
        return this.aioConfig;
    }

    public void setChannelFutureListener(ChannelFutureListener channelFutureListener) {
        this.channelFutureListener = channelFutureListener;
    }

    @Override
    public Void apply(BufferWriter input) {
        //获取信息量
        if (!semaphore.tryAcquire()) {
            return null;
        }
        AioChannel.this.writeByteBuffer = input.poll();
        if (null == writeByteBuffer) {
            semaphore.release();
        } else {
            AioChannel.this.continueWrite(writeByteBuffer);
        }
        return null;
    }
}
