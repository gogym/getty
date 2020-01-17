/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.buffer;

import com.gettyio.core.function.Function;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonBlockQueue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

/**
 * 类名：BufferWriter.java
 * 描述：数据输出器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class BufferWriter extends OutputStream {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ByteBuffer.class);
    //缓冲池
    private final ChunkPool chunkPool;
    private int chunkPoolBlockTime;
    private final Function<BufferWriter, Void> function;
    //当前是否已关闭
    private boolean closed = false;
    //阻塞队列
    private final LinkedNonBlockQueue<ByteBuffer> queue;

    public LinkedNonBlockQueue<ByteBuffer> getQueue() {
        return queue;
    }

    public BufferWriter(ChunkPool chunkPool, Function<BufferWriter, Void> flushFunction, int bufferWriterQueueSize, int chunkPoolBlockTime) {
        this.chunkPool = chunkPool;
        this.chunkPoolBlockTime = chunkPoolBlockTime;
        this.function = flushFunction;
        queue = new LinkedNonBlockQueue<>(bufferWriterQueueSize);
    }

    @Deprecated
    @Override
    public void write(int b) throws IOException {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (b & 0xFF);
        bytes[1] = (byte) ((b >> 8) & 0xFF);
        bytes[2] = (byte) ((b >> 16) & 0xFF);
        bytes[3] = (byte) ((b >> 24) & 0xFF);
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            IOException ioException = new IOException("OutputStream has closed");
            logger.error(ioException.getMessage(), ioException);
            throw ioException;
        }
        if (len <= 0 || b.length == 0) {
            return;
        }

        try {
            //申请写缓冲
            ByteBuffer chunkPage = chunkPool.allocate(len - off, chunkPoolBlockTime);
            int minSize = chunkPage.remaining();
            if (minSize == 0) {
                chunkPool.deallocate(chunkPage);
                throw new RuntimeException("ByteBuffer remaining is 0");
            }
            //写入数据
            chunkPage.put(b, off, b.length);
            //if (!chunkPage.hasRemaining()) {
            chunkPage.flip();
            //已经读取完，写到缓冲队列
            queue.put(chunkPage);
            // }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }


    /**
     * 刷新缓冲区。
     *
     * @param b 数组
     * @throws IOException 抛出异常
     */
    public void writeAndFlush(byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        writeAndFlush(b, 0, b.length);
    }

    /**
     * @param b   待输出数据
     * @param off 起始位点
     * @param len 输出的数据长度
     * @throws IOException 抛出异常
     */
    private void writeAndFlush(byte[] b, int off, int len) throws IOException {
        write(b, off, len);
        flush();
    }

    @Override
    public void flush() {
        if (closed) {
            throw new RuntimeException("OutputStream has closed");
        }
        function.apply(this);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("OutputStream has closed");
        }
        while (queue.getCount() > 0) {
            flush();
        }
        closed = true;
        if (chunkPool != null) {
            //清空内存池
            chunkPool.clear();
        }
    }

    public boolean isClosed() {
        return closed;
    }


    public ByteBuffer poll() {
        try {
            return queue.poll();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }


}