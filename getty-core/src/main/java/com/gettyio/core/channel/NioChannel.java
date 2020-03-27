package com.gettyio.core.channel;/*
 * 类名：UdpChannel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/17
 */

import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.config.AioConfig;
import com.gettyio.core.function.Function;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.LinkedBlockQueue;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NioChannel extends AioChannel implements Function<BufferWriter, Void> {

    private SocketChannel channel;
    //selector
    private Selector selector;

    protected BufferWriter bufferWriter;

    /**
     * 写缓冲
     */
    protected ByteBuffer writeByteBuffer;

    /**
     * 输出信号量
     */
    private Semaphore semaphore = new Semaphore(1);

    //线程池
    private int workerThreadNum = 3;

    public NioChannel(SocketChannel channel, AioConfig config, ChunkPool chunkPool, ChannelPipeline channelPipeline) {
        this.channel = channel;
        this.aioConfig = config;
        this.chunkPool = chunkPool;
        try {
            this.selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);

            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            close();
            throw new RuntimeException("SocketChannel init exception", e);
        }

        //初始化数据输出类
        bufferWriter = new BufferWriter(chunkPool, this, config.getBufferWriterQueueSize(), config.getChunkPoolBlockTime());

        //触发责任链
        try {
            invokePipeline(ChannelState.NEW_CHANNEL);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    @Override
    public void starRead() {
        ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);

        for (int i = 0; i < workerThreadNum; i++) {
            workerThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (selector.select() > 0) {
                            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                            while (it.hasNext()) {
                                SelectionKey sk = it.next();
                                if (sk.isConnectable()) {
                                    SocketChannel channel = (SocketChannel) sk.channel();
                                    //during connecting, finish the connect
                                    if (channel.isConnectionPending()) {
                                        channel.finishConnect();
                                    }
                                } else if (sk.isReadable()) {
                                    ByteBuffer readBuffer = chunkPool.allocate(aioConfig.getReadBufferSize(), aioConfig.getChunkPoolBlockTime());
                                    //接收数据
                                    ((SocketChannel) sk.channel()).read(readBuffer);

                                    //读取缓冲区数据到管道
                                    if (null != readBuffer) {

                                        readBuffer.flip();
                                        //读取缓冲区数据，输送到责任链
                                        while (readBuffer.hasRemaining()) {
                                            byte[] bytes = new byte[readBuffer.remaining()];
                                            readBuffer.get(bytes, 0, bytes.length);
                                            try {
                                                readToPipeline(bytes);
                                            } catch (Exception e) {
                                                logger.error(e);
                                                close();
                                            }
                                        }
                                        //触发读取完成，清理缓冲区
                                        chunkPool.deallocate(readBuffer);
                                    }
                                }
                            }
                            it.remove();
                        }
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            });
        }

    }


    @Override
    public void close() {

        if (status == CHANNEL_STATUS_CLOSED) {
            logger.warn("Channel:{} is closed:", getChannelId());
            return;
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
        try {
            invokePipeline(ChannelState.CHANNEL_CLOSED);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //最后需要清空责任链
        if (defaultChannelPipeline != null) {
            defaultChannelPipeline.clean();
            defaultChannelPipeline = null;
        }

    }

    @Override
    public void writeAndFlush(Object obj) {
        try {
            reverseInvokePipeline(ChannelState.CHANNEL_WRITE, obj);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void writeToChannel(Object obj) {
        try {
            bufferWriter.writeAndFlush((byte[]) obj);
        } catch (IOException e) {
            logger.error(e);
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getLocalAddress();
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


    /**
     * 继续写
     */
    private void continueWrite() {

        if (writeByteBuffer == null) {
            writeByteBuffer = bufferWriter.poll();
        } else if (!writeByteBuffer.hasRemaining()) {
            //写完及时释放
            chunkPool.deallocate(writeByteBuffer);
            writeByteBuffer = bufferWriter.poll();
        }

        if (writeByteBuffer != null) {
            //再次写
            try {
                channel.write(writeByteBuffer);
            } catch (IOException e) {
                logger.error("write error", e);
            }
            //这里为了确保这个线程可以完全写完需要输出的数据。因此循环输出，直至输出完毕
            continueWrite();
        }
        //完全写完释放信息量
        semaphore.release();

        if (!keepAlive) {
            this.close();
        }
    }


    @Override
    public Void apply(BufferWriter input) {
        //获取信息量
        if (!semaphore.tryAcquire()) {
            return null;
        }
        NioChannel.this.writeByteBuffer = input.poll();
        if (null == writeByteBuffer) {
            semaphore.release();
        } else {
            NioChannel.this.continueWrite();
        }
        return null;
    }

}
