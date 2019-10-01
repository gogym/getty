/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.buffer;

import org.getty.core.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 类名：BufferWriter.java
 * 描述：数据输出器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class BufferWriter extends OutputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferWriter.class);
    //缓冲页
    private final Chunk chunk;
    //当前缓冲区，写出完塞到items
    private ChunkPage chunkPage;
    //就绪待输出数据
    private final ChunkPage[] items;
    // items 中存放的缓冲数据数量
    private int count;
    //函数
    private final Function<BufferWriter, Void> function;
    //同步锁
    private final ReentrantLock lock = new ReentrantLock();
    //Condition实现线程等待通知机制
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final Condition waiting = lock.newCondition();
    private volatile boolean isWaiting = false;
    //items 读索引位
    private int takeIndex;
    // items 写索引位
    private int putIndex;

    //当前是否已关闭
    private boolean closed = false;

    public BufferWriter(Chunk bufferPage, Function<BufferWriter, Void> flushFunction, int writeQueueSize) {
        this.chunk = bufferPage;
        this.function = flushFunction;
        this.items = new ChunkPage[writeQueueSize];
    }

    /**
     * @throws IOException
     * @deprecated
     */
    @Deprecated
    @Override
    public void write(int b) throws IOException {
        throw new IOException("don't use this method");
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("OutputStream has closed");
        }
        if (len == 0 || b.length == 0) {
            return;
        }
        if (off < 0 || off > b.length) {
            throw new IndexOutOfBoundsException();
        }

        //上锁
        lock.lock();
        try {
            //判断当前是否有任务在写
            // waitPreWriteFinish();
            do {
                if (chunkPage == null) {
                    //申请写缓冲
                    chunkPage = chunk.allocate(len - off);
                }

                ByteBuffer writeBuffer = chunkPage.buffer();
                int minSize = writeBuffer.remaining();
                if (minSize == 0 || closed) {
                    chunkPage.clean();
                    throw new IOException("writeBuffer.remaining:" + writeBuffer.remaining() + " closed:" + closed);
                }
                //写入数据
                writeBuffer.put(b, off, minSize);
                off += minSize;

                if (!writeBuffer.hasRemaining()) {
                    //已经读取完，写到缓冲队列
                    writeBuffer.flip();
                    this.put(chunkPage);
                    chunkPage = null;
                }
            } while (off < len);

            //唤醒等待中的线程
            // notifyWaiting();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 唤醒处于waiting状态的线程
     */
    private void notifyWaiting() {
        isWaiting = false;
        //唤醒线程
        waiting.signal();
    }

    /**
     * 确保数据输出有序性
     *
     * @throws IOException
     */
    private void waitPreWriteFinish() throws IOException {
        while (isWaiting) {
            try {
                //线程等待
                waiting.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
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
        if (this.count > 0) {
            function.apply(this);
        }

    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (closed) {
                throw new IOException("OutputStream has closed");
            }
            flush();
            closed = true;

            ChunkPage byteBuf;
            while ((byteBuf = poll()) != null) {
                byteBuf.clean();
            }
            if (chunkPage != null) {
                chunkPage.clean();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        return closed;
    }


    /**
     * 存储缓冲区至队列中以备输出
     *
     * @param e
     */
    private void put(ChunkPage e) {
        try {
            while (count == items.length) {
                isWaiting = true;
                //队列已满，等待
                notFull.await();
            }
            items[putIndex] = e;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;
            notEmpty.signal();
        } catch (InterruptedException e1) {
            throw new RuntimeException(e1);
        }
    }

    /**
     * 获取并移除当前缓冲队列中头部
     */
    public ChunkPage poll() {
        lock.lock();
        try {
            if (count == 0) {
                return null;
            }
            ChunkPage x = items[takeIndex];
            items[takeIndex] = null;
            if (++takeIndex == items.length) {
                takeIndex = 0;
            }
            count--;
            notFull.signal();
            return x;
        } finally {
            lock.unlock();
        }
    }

}