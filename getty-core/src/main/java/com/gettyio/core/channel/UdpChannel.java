package com.gettyio.core.channel;/*
 * 类名：UdpChannel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/17
 */

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.buffer.LinkedBlockQueue;
import com.gettyio.core.channel.config.AioConfig;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

public class UdpChannel extends AioChannel {


    //udp通道
    private DatagramChannel datagramChannel;
    //selector
    private Selector selector;
    //阻塞队列
    private LinkedBlockQueue<Object> queue;
    //线程池
    private int workerThreadNum;

    public UdpChannel(DatagramChannel datagramChannel, Selector selector, AioConfig config, ChunkPool chunkPool, ChannelPipeline channelPipeline, int workerThreadNum) {
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        this.aioConfig = config;
        this.chunkPool = chunkPool;
        this.workerThreadNum = workerThreadNum;
        queue = new LinkedBlockQueue<>(config.getBufferWriterQueueSize());
        try {
            //注意该方法可能抛异常
            channelPipeline.initChannel(this);
        } catch (Exception e) {
            throw new RuntimeException("channelPipeline init exception", e);
        }

        //开启写监听线程
        loopWrite();
        //触发责任链回调
        invokePipeline(ChannelState.NEW_CHANNEL);
    }


    @Override
    public void starRead() {
        ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        for (int i = 0; i < workerThreadNum; i++) {
            workerThreadPool.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (selector.select() > 0) {
                                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                                    while (it.hasNext()) {
                                        SelectionKey sk = it.next();
                                        if (sk.isReadable()) {
                                            ByteBuffer readBuffer = chunkPool.allocate(aioConfig.getReadBufferSize(), aioConfig.getChunkPoolBlockTime());
                                            //接收数据
                                            InetSocketAddress address = (InetSocketAddress) datagramChannel.receive(readBuffer);
                                            if (null != readBuffer) {
                                                readBuffer.flip();
                                                //读取缓冲区数据，输送到责任链
                                                while (readBuffer.hasRemaining()) {
                                                    byte[] bytes = new byte[readBuffer.remaining()];
                                                    readBuffer.get(bytes, 0, bytes.length);
                                                    //读取的数据封装成DatagramPacket
                                                    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, address);
                                                    //输出到链条
                                                    UdpChannel.this.readToPipeline(datagramPacket);
                                                }
                                                chunkPool.deallocate(readBuffer);
                                            }
                                        }
                                    }
                                    it.remove();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (TimeoutException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }


    /**
     * 多线程持续写出
     */
    private void loopWrite() {
        ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, workerThreadNum);
        for (int i = 0; i < workerThreadNum; i++) {
            workerThreadPool.execute(() -> {
                try {
                    Object obj;
                    while ((obj = queue.poll()) != null) {
                        send(obj);
                    }
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
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
        try {
            datagramChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (channelFutureListener != null) {
            channelFutureListener.operationComplete(this);
        }
        //更新状态
        status = CHANNEL_STATUS_CLOSED;

        //最后需要清空责任链
        if (defaultChannelPipeline != null) {
            defaultChannelPipeline.clean();
            defaultChannelPipeline = null;
        }
    }

    @Override
    public void writeAndFlush(Object obj) {
        try {
            queue.put(obj);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    @Deprecated
    public void writeToChannel(Object obj) {
        try {
            queue.put(obj);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) datagramChannel.getLocalAddress();
    }

    /**
     * 断言
     *
     * @throws IOException 异常
     */
    private void assertChannel() throws IOException {
        if (status == CHANNEL_STATUS_CLOSED || datagramChannel == null) {
            throw new IOException("channel is closed");
        }
    }


    /**
     * 往目标地址发送消息
     *
     * @return void
     * @params [obj]
     */
    private void send(Object obj) {
        try {
            //转换成udp数据包
            DatagramPacket datagramPacket = (DatagramPacket) obj;
            ByteBuffer byteBuffer = chunkPool.allocate(datagramPacket.getLength(), aioConfig.getChunkPoolBlockTime());
            byteBuffer.put(datagramPacket.getData());
            byteBuffer.flip();
            //写出到目标地址
            datagramChannel.send(byteBuffer, datagramPacket.getSocketAddress());
            //释放内存
            chunkPool.deallocate(byteBuffer);
        } catch (ClassCastException e) {
            logger.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }


}
