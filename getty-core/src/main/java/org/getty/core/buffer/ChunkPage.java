/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.buffer;

import java.nio.ByteBuffer;

/**
 * 类名：ChunkPage.java
 * 描述：小内存块
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class ChunkPage {

    //当前的所属内存块
    private final Chunk chunk;
    //ByteBuffer小内存块
    private ByteBuffer buffer;

    private boolean clean = false;
    //当前实际position
    private int position;
    //当前实际limit
    private int limit;

    ChunkPage(Chunk chunk, ByteBuffer buffer, int position, int limit) {
        this.chunk = chunk;
        this.buffer = buffer;
        this.position = position;
        this.limit = limit;
    }


    public ByteBuffer buffer() {
        return buffer;
    }

    void buffer(ByteBuffer buffer) {
        this.buffer = buffer;
        clean = false;
    }


    /**
     * 返回limit和position之间相对位置差
     *
     * @return
     */
    public int remaining() {
        return limit - position;
    }


    public void clean() {
        if (clean) {
            throw new RuntimeException("buffer has cleaned");
        }
        clean = true;
        if (chunk != null) {
            chunk.clean(this);
        } else {
            buffer = null;
        }
    }

    @Override
    public String toString() {
        return "ChunkPage{position=" + position + ", limit=" + limit + '}';
    }


    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Chunk getChunk() {
        return chunk;
    }
}
