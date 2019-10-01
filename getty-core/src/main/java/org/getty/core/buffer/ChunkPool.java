/**
 * 包名：org.getty.core.buffer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.buffer;

/**
 * 类名：ChunkPool.java
 * 描述：内存池
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class ChunkPool {
    private Chunk[] bufferPageList;

    private volatile int cursor = -1;

    /**
     * @param pageSize 大小
     * @param poolSize 个数
     */
    public ChunkPool(final int pageSize, final int poolSize, final boolean isDirect) {
        bufferPageList = new Chunk[poolSize];
        for (int i = 0; i < poolSize; i++) {
            bufferPageList[i] = new Chunk(pageSize, isDirect);
        }
    }

    public Chunk allocateBufferPage() {
        cursor = (cursor + 1) % bufferPageList.length;
        Chunk page = bufferPageList[cursor];
        return page;
    }
}
