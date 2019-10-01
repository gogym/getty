/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 类名：Chunk.java
 * 描述：内存块对象
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class Chunk {
    private static final Logger LOGGER = LoggerFactory.getLogger(Chunk.class);
    /**
     * 当前可用的缓冲区
     */
    private List<ChunkPage> availableBuffers = new LinkedList<>();
    /**
     * 当前缓存页总的缓冲区
     */
    private ByteBuffer buffer;
    /**
     * 同步锁
     */
    private ReentrantLock lock = new ReentrantLock();
    /**
     * 是否堆内
     */
    private boolean direct;

    /**
     * @param size
     * @param direct
     */
    Chunk(int size, boolean direct) {
        this.buffer = allocate0(size, direct);
        this.direct = direct;
        ChunkPage chunkPage = new ChunkPage(this, null, buffer.position(), buffer.limit());
        availableBuffers.add(chunkPage);
    }

    /**
     * 申请内存
     *
     * @param size   大小
     * @param direct true:堆外缓冲区,false:堆内缓冲区
     * @return 缓冲区
     */
    private ByteBuffer allocate0(int size, boolean direct) {
        return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }


    /**
     * 申请内存
     *
     * @param size
     * @return
     */
    public ChunkPage allocate(final int size) {
        lock.lock();
        try {
            //当前空闲的虚拟缓冲区
            Iterator<ChunkPage> iterator = availableBuffers.iterator();
            ChunkPage bufferChunk;
            while (iterator.hasNext()) {
                ChunkPage freeChunk = iterator.next();
                //Return limit - position;返回limit和position之间相对位置差
                final int remaining = freeChunk.remaining();
                if (remaining < size) {
                    continue;
                }
                if (remaining == size) {
                    //大小相等，分配空间
                    iterator.remove();
                    //标记到总的缓冲区中
                    buffer.limit(freeChunk.getLimit());
                    buffer.position(freeChunk.getPosition());
                    //缓冲区的分片，建立子缓冲区
                    freeChunk.buffer(buffer.slice());
                    bufferChunk = freeChunk;
                } else {
                    //如果空闲的分区比需要分配的大，则按需分割。
                    //这句相当于把limit往前移动，设置刚好满足大小的子缓冲区
                    buffer.limit(freeChunk.getPosition() + size);
                    buffer.position(freeChunk.getPosition());
                    //创建新的子缓冲区
                    bufferChunk = new ChunkPage(this, buffer.slice(), buffer.position(), buffer.limit());
                    //当前被分割的子缓冲区重新定位
                    freeChunk.setPosition(buffer.limit());
                }
                //大小不匹配，抛个异常
                if (bufferChunk.buffer().remaining() != size) {
                    throw new RuntimeException("allocate " + size + ", buffer:" + bufferChunk);
                }
                return bufferChunk;
            }
        } finally {
            lock.unlock();
        }

//        if (LOGGER.isDebugEnabled()) {
        //没有必要时不要打开，并发时性能影响巨大
//            LOGGER.warn("bufferPage has no available space: " + size);
//        }
        //如果没有足够的空间，则分配一个堆内空间,使用完后快速清理
        return new ChunkPage(null, allocate0(size, false), 0, 0);

    }

    /**
     * 清理缓冲区
     *
     * @param cleanBuffer
     */
    void clean(ChunkPage cleanBuffer) {
        if (cleanBuffer.getChunk() == null) {
            return;
        }
        //尝试获取锁
        if (!lock.tryLock()) {
            //当锁不可用，那么当前线程被阻塞，休眠一直到该锁可以获取
            lock.lock();
        }
        try {
            //清理缓冲区
            clean0(cleanBuffer);
            //循环清理缓冲区
        } finally {
            //最后要释放锁
            lock.unlock();
        }
    }

    private void clean0(ChunkPage cleanBuffer) {
        int index = 0;
        Iterator<ChunkPage> iterator = availableBuffers.iterator();

        while (iterator.hasNext()) {
            ChunkPage freeBuffer = iterator.next();
            //如果当前可用的缓冲区的下标刚好等于清理缓冲区的limit,则把下标移动到清理缓冲区的下标处。
            if (freeBuffer.getPosition() == cleanBuffer.getLimit()) {
                freeBuffer.setPosition(cleanBuffer.getPosition());
                return;
            }
            //反之如果可用缓冲区的limi刚好等于清理的position。则移动到清理的缓冲区limit处，使着连贯起来
            if (freeBuffer.getLimit() == cleanBuffer.getPosition()) {
                freeBuffer.setLimit(cleanBuffer.getLimit());
                if (iterator.hasNext()) {
                    ChunkPage next = iterator.next();
                    //如果下一个的下标刚好在空闲的limit处
                    if (next.getPosition() == freeBuffer.getLimit()) {
                        //合并在一起
                        freeBuffer.setLimit(next.getLimit());
                        //移除原有的
                        iterator.remove();
                    } else if (next.getPosition() < freeBuffer.getLimit()) {
                        throw new IllegalStateException("缓存区clean错误");
                    }
                }
                return;
            }
            if (freeBuffer.getPosition() > cleanBuffer.getLimit()) {
                availableBuffers.add(index, cleanBuffer);
                return;
            }
            index++;
        }
        availableBuffers.add(cleanBuffer);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "availableBuffers=" + availableBuffers + '}';
    }
}
