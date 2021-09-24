
package com.gettyio.core.buffer.pool;


import com.gettyio.core.buffer.pool.buffer.PooledByteBuf;
import com.gettyio.core.util.StringUtil;

final class PoolChunkList<T> {
    /**
     * 池化空间
     */
    private final PoolArena<T> arena;
    /**
     * 下一个块列表
     */
    private final PoolChunkList<T> nextList;
    /**
     * 上一个块列表
     */
    PoolChunkList<T> prevList;

    /**
     * 最小使用率
     */
    private final int minUsage;
    /**
     * 最大使用率
     */
    private final int maxUsage;

    /**
     * 池化堆内存块
     */
    private PoolChunk<T> head;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage) {
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (head == null) {
            return false;
        }

        for (PoolChunk<T> cur = head; ; ) {
            long handle = cur.allocate(normCapacity);
            if (handle < 0) {
                cur = cur.next;
                if (cur == null) {
                    return false;
                }
            } else {
                cur.initBuf(buf, handle, reqCapacity);
                if (cur.usage() >= maxUsage) {
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
    }

    void free(PoolChunk<T> chunk, long handle) {
        chunk.free(handle);
        if (chunk.usage() < minUsage) {
            remove(chunk);
            if (prevList == null) {
                assert chunk.usage() == 0;
                arena.destroyChunk(chunk);
            } else {
                prevList.add(chunk);
            }
        }
    }

    void add(PoolChunk<T> chunk) {
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk);
            return;
        }

        chunk.parent = this;
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    @Override
    public String toString() {
        if (head == null) {
            return "none";
        }

        StringBuilder buf = new StringBuilder();
        for (PoolChunk<T> cur = head; ; ) {
            buf.append(cur);
            cur = cur.next;
            if (cur == null) {
                break;
            }
            buf.append(StringUtil.NEWLINE);
        }

        return buf.toString();
    }
}
