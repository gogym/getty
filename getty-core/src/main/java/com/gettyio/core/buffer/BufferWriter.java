/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.buffer;

import com.gettyio.core.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(BufferWriter.class);
    //缓冲页
    private final ChunkPool chunkPool;
    private final Function<BufferWriter, Void> function;
    //当前是否已关闭
    private boolean closed = false;

    private final ByteBuffer[] items = new ByteBuffer[2 * 1024 * 1024];
    private int count;
    private int takeIndex;
    private int putIndex;


    public BufferWriter(ChunkPool chunkPool, Function<BufferWriter, Void> flushFunction) {
        this.chunkPool = chunkPool;
        this.function = flushFunction;
    }

    /**
     * @throws IOException
     * @deprecated
     */
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
            ByteBuffer chunkPage = chunkPool.allocate(len - off, 1000);
            int minSize = chunkPage.remaining();
            if (minSize == 0) {
                chunkPool.deallocate(chunkPage);
                throw new RuntimeException("ByteBuffer remaining is 0");
            }
            //写入数据
            chunkPage.put(b, off, minSize);
            if (!chunkPage.hasRemaining()) {
                //已经读取完，写到缓冲队列
                chunkPage.flip();
                this.put(chunkPage);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }


    /**
     * 刷新缓冲区。
     *
     * @throws IOException
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
     * @throws IOException
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
        flush();
        closed = true;
        if (chunkPool != null) {
            //清空内存池
            chunkPool.clear();
        }
    }

    public boolean isClosed() {
        return closed;
    }


    private void put(ByteBuffer e) {
        items[putIndex] = e;
        if (++putIndex == items.length) {
            putIndex = 0;
        }
        count++;
    }

    public ByteBuffer poll() {
        if (count == 0) {
            return null;
        }
        ByteBuffer x = items[takeIndex];
        items[takeIndex] = null;
        if (++takeIndex == items.length) {
            takeIndex = 0;
        }
        count--;
        return x;
    }


}