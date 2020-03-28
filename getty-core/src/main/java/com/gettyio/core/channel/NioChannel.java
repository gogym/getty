package com.gettyio.core.channel;/*
 * 类名：UdpChannel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/17
 */

import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.function.Function;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

public class NioChannel extends SocketChannel implements Function<BufferWriter, Void> {

    private java.nio.channels.SocketChannel channel;
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
    private int workerThreadNum;
    ThreadPool workerThreadPool;

    public NioChannel(java.nio.channels.SocketChannel channel, BaseConfig config, ChunkPool chunkPool, Integer workerThreadNum, ChannelPipeline channelPipeline) {
        this.channel = channel;
        this.aioConfig = config;
        this.chunkPool = chunkPool;
        this.workerThreadNum = workerThreadNum;
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
        workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        //触发责任链
        try {
            invokePipeline(ChannelState.NEW_CHANNEL);
        } catch (Exception e) {
            logger.error(e);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                NioChannel.this.continueWrite();
            }
        }).start();

    }


    @Override
    public void starRead() {
        //多线程处理，提高效率
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
                                    java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
                                    //during connecting, finish the connect
                                    if (channel.isConnectionPending()) {
                                        channel.finishConnect();
                                    }
                                } else if (sk.isReadable()) {
                                    ByteBuffer readBuffer = chunkPool.allocate(aioConfig.getReadBufferSize(), aioConfig.getChunkPoolBlockTime());
                                    //接收数据
                                    ((java.nio.channels.SocketChannel) sk.channel()).read(readBuffer);

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
                                    }
                                    //触发读取完成，清理缓冲区
                                    chunkPool.deallocate(readBuffer);
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
            //bufferWriter.writeAndFlush((byte[]) obj);
            byte[] bytes = (byte[]) obj;
            bufferWriter.write(bytes, 0, bytes.length);
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

        while (true) {
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
                    NioChannel.this.close();
                    logger.error("write error", e);
                    break;
                }
            }
            if (!keepAlive) {
                NioChannel.this.close();
                break;
            }
        }
    }


    @Override
    public Void apply(BufferWriter input) {
        return null;
    }

}
