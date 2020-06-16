package com.gettyio.core.channel.loop;/*
 * 类名：EventLoop
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/6/16
 */

import com.gettyio.core.buffer.BufferWriter;
import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.channel.NioChannel;
import com.gettyio.core.channel.config.BaseConfig;
import com.gettyio.core.function.Function;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

public class NioEventLoop implements Function<BufferWriter, Void> {

    protected BaseConfig serverConfig;

    /**
     * selector包装
     */
    private SelectedSelector selector;

    /**
     * 创建一个2个线程的线程池，负责读和写
     */
    private ThreadPool workerThreadPool;
    /**
     * 内存池
     */
    protected ChunkPool chunkPool;
    /**
     * 数据输出类
     */
    protected BufferWriter bufferWriter;

    /**
     * 写缓冲
     */
    protected ByteBuffer writeByteBuffer;


    public NioEventLoop(BaseConfig serverConfig, ChunkPool chunkPool) {
        this.serverConfig = serverConfig;
        this.chunkPool = chunkPool;
        this.workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 2);
        //初始化数据输出类
        bufferWriter = new BufferWriter(BufferWriter.BLOCK, chunkPool, this, serverConfig.getBufferWriterQueueSize(), serverConfig.getChunkPoolBlockTime());
        try {
            selector = new SelectedSelector(Selector.open());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void run() {
        //读
        workerThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        selector.select();
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey sk = it.next();
                            Object obj = sk.attachment();
                            if (obj instanceof NioChannel) {
                                NioChannel nioChannel = (NioChannel) obj;

                                if (sk.isConnectable()) {
                                    java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) sk.channel();
                                    //during connecting, finish the connect
                                    if (channel.isConnectionPending()) {
                                        channel.finishConnect();
                                    }
                                } else if (sk.isReadable()) {
                                    ByteBuffer readBuffer = chunkPool.allocate(serverConfig.getReadBufferSize(), serverConfig.getChunkPoolBlockTime());
                                    //接收数据
                                    int reccount = ((java.nio.channels.SocketChannel) sk.channel()).read(readBuffer);
                                    if (reccount == -1) {
                                        chunkPool.deallocate(readBuffer);
                                        nioChannel.close();
                                        return;
                                    }

                                    //读取缓冲区数据到管道
                                    if (null != readBuffer) {
                                        readBuffer.flip();
                                        //读取缓冲区数据，输送到责任链
                                        while (readBuffer.hasRemaining()) {
                                            byte[] bytes = new byte[readBuffer.remaining()];
                                            readBuffer.get(bytes, 0, bytes.length);
                                            // nioChannel.starRead(bytes);
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
                    return;
                }

            }
        });
    }


    public SelectedSelector getSelector() {
        return selector;
    }

    public BufferWriter getBufferWriter() {
        return bufferWriter;
    }

    @Override
    public Void apply(BufferWriter input) {
        return null;
    }


}
