package com.gettyio.core.buffer.pool.buffer;




import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.buffer.buffer.AbstractReferenceCountedByteBuf;
import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.buffer.pool.PoolChunk;
import com.gettyio.core.buffer.pool.Recycler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 池化的缓冲区基类
 *
 * @param <T>
 */
public abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {

    private final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;

    /**
     * 内存池块
     */
    public PoolChunk<T> chunk;
    public long handle;
    public T memory;
    public int offset;
    public int length;
    public int maxLength;
    public Thread initThread;
    private ByteBuffer tmpNioBuf;

    @SuppressWarnings("unchecked")
    protected PooledByteBuf(Recycler.Handle<? extends PooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (Recycler.Handle<PooledByteBuf<T>>) recyclerHandle;
    }

    public void init(PoolChunk<T> chunk, long handle, int offset, int length, int maxLength) {
        assert handle >= 0;
        assert chunk != null;

        this.chunk = chunk;
        this.handle = handle;
        memory = chunk.memory;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
        setIndex(0, 0);
        tmpNioBuf = null;
        initThread = Thread.currentThread();
    }

    public void initUnpooled(PoolChunk<T> chunk, int length) {
        assert chunk != null;

        this.chunk = chunk;
        handle = 0;
        memory = chunk.memory;
        offset = 0;
        this.length = maxLength = length;
        setIndex(0, 0);
        tmpNioBuf = null;
        initThread = Thread.currentThread();
    }

    @Override
    public final int capacity() {
        return length;
    }

    @Override
    public final ByteBuf capacity(int newCapacity) {
        ensureAccessible();

        // 如果请求容量不需要重新分配，只需更新内存长度。
        if (chunk.unpooled) {
            if (newCapacity == length) {
                return this;
            }
        } else {
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity < length) {
                if (newCapacity > maxLength >>> 1) {
                    if (maxLength <= 512) {
                        if (newCapacity > maxLength - 16) {
                            length = newCapacity;
                            setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                            return this;
                        }
                    } else { // > 512 (i.e. >= 1024)
                        length = newCapacity;
                        setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                        return this;
                    }
                }
            } else {
                return this;
            }
        }

        // 需要重新分配。
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return chunk.arena.parent;
    }

    @Override
    public final ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public final ByteBuf unwrap() {
        return null;
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        }
        return tmpNioBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            byteBuffer=null;
            memory = null;
            boolean sameThread = initThread == Thread.currentThread();
            initThread = null;
            chunk.arena.free(chunk, handle, maxLength, sameThread);
            recycle();
        }
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    protected final int idx(int index) {
        return offset + index;
    }
}
