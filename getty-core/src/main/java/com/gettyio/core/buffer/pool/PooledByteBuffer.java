/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.buffer.pool;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PooledByteBuffer 是内存池中的核心缓冲区对象，继承自 {@link RetainableByteBuffer}。
 * <p>
 * 它是三级架构中用户直接操作的缓冲区对象，封装了从 {@link PoolChunk} 分配的内存切片，
 * 并通过引用计数管理其生命周期。当引用计数归零时，内存会自动归还给线程缓存或 Arena。
 * </p>
 *
 * <h3>生命周期：</h3>
 * <pre>
 *   1. 从池中分配 → refCount = 1（可用）
 *   2. retain() → refCount++（共享引用）
 *   3. release() → refCount--
 *   4. refCount == 0 → 归还给 PoolThreadCache/PoolArena
 * </pre>
 *
 * <h3>与 RetainableByteBuffer 的兼容性：</h3>
 * <p>
 * PooledByteBuffer 继承 RetainableByteBuffer，完全兼容现有的 API 调用方式，
 * 包括 getBuffer()、flipToFill()、flipToFlush()、put()、get() 等方法。
 * 现有代码无需修改即可使用新的池化实现。
 * </p>
 *
 * @author Getty Project
 */
public class PooledByteBuffer extends RetainableByteBuffer {

    // ======================== 池关联字段 ========================

    /**
     * 此缓冲区所属的 PoolThreadCache。
     * 释放时优先归还到此缓存（零延迟路径）。
     */
    private final PoolThreadCache threadCache;

    /**
     * 此缓冲区所属的 PoolChunk。
     * 记录了内存分配的具体来源，用于精确归还。
     * 如果是巨型分配（不经过池），则为 null。
     */
    private final PoolChunk chunk;

    /**
     * 此缓冲区在 Chunk 中的内存偏移量（字节）。
     * 与 chunk 配合使用，用于归还时定位内存位置。
     */
    private final int chunkOffset;

    /**
     * 分配时的规范化容量。
     * Chunk 中实际分配的大小（向上取整到 size class），
     * 归还时必须使用此值，而非用户的原始请求大小。
     */
    private final int normCapacity;

    // ======================== 引用计数 ========================

    /**
     * 引用计数器。
     * <ul>
     *   <li>0: 未激活 / 已释放</li>
     *   <li>1: 从池中获取，单个持有者</li>
     *   <li>>1: 被 retain() 过，有多个共享引用</li>
     * </ul>
     * 使用 AtomicInteger 保证多线程安全。
     */
    private final AtomicInteger refCount = new AtomicInteger(0);

    /**
     * 最后一次操作的时间戳（纳秒），用于 LRU 淘汰。
     */
    private final AtomicLong lastUpdateTime = new AtomicLong(System.nanoTime());

    // ======================== 构造 ========================

    /**
     * 构造一个池化的 ByteBuffer。
     *
     * @param buffer       从 PoolChunk 分配的 ByteBuffer 切片
     * @param threadCache  所属的线程缓存（释放时优先归还到这里）
     * @param chunk        所属的 PoolChunk（巨型分配时为 null）
     * @param chunkOffset  在 Chunk 中的偏移量
     * @param normCapacity 规范化容量
     */
    public PooledByteBuffer(ByteBuffer buffer, PoolThreadCache threadCache,
                            PoolChunk chunk, int chunkOffset, int normCapacity) {
        super(buffer, null); // 不使用 RetainableByteBuffer 的 releaser 机制
        this.threadCache = threadCache;
        this.chunk = chunk;
        this.chunkOffset = chunkOffset;
        this.normCapacity = normCapacity;
    }

    // ======================== 生命周期管理 ========================

    /**
     * 激活缓冲区（从池中获取后调用）。
     * <p>
     * 将引用计数从 0 设为 1，标记缓冲区为"正在使用"状态。
     * 如果引用计数不为 0，说明缓冲区还在被使用，抛出异常。
     * </p>
     *
     * @throws IllegalStateException 如果缓冲区仍在使用中
     */
    void activate() {
        if (!refCount.compareAndSet(0, 1)) {
            throw new IllegalStateException("PooledByteBuffer is still in use: " + this);
        }
        lastUpdateTime.set(System.nanoTime());
    }

    /**
     * 增加引用计数（共享引用）。
     * <p>
     * 当多个组件需要共享同一个缓冲区时调用。每次 retain() 必须对应一次 release()。
     * </p>
     *
     * @throws IllegalStateException 如果缓冲区已被释放（refCount == 0）
     */
    public void retain() {
        int current = refCount.getAndUpdate(c -> c == 0 ? 0 : c + 1);
        if (current == 0) {
            throw new IllegalStateException("Cannot retain a released PooledByteBuffer: " + this);
        }
    }

    /**
     * 减少引用计数。
     * <p>
     * 当引用计数归零时，缓冲区自动归还给池：
     * <ol>
     *   <li>优先归还给当前线程的 {@link PoolThreadCache}（零延迟）</li>
     *   <li>如果线程缓存已满，由 ThreadCache 归还给 {@link PoolArena}</li>
     *   <li>如果是巨型分配（chunk == null），直接丢弃等待 GC</li>
     * </ol>
     * </p>
     *
     * @return true 如果缓冲区已归还给池（refCount == 0）
     * @throws IllegalStateException 如果缓冲区已被释放（重复 release）
     */
    @Override
    public boolean release() {
        int ref = refCount.updateAndGet(c -> {
            if (c == 0) {
                throw new IllegalStateException("PooledByteBuffer already released: " + this);
            }
            return c - 1;
        });

        if (ref == 0) {
            lastUpdateTime.set(System.nanoTime());
            recycle();
            return true;
        }
        return false;
    }

    /**
     * 将缓冲区归还给池。
     * <p>
     * 重置底层 ByteBuffer 的状态，然后归还给 ThreadCache 或 Arena。
     * </p>
     */
    private void recycle() {
        ByteBuffer buf = getBuffer();
        if (buf != null) {
            BufferUtil.reset(buf);
        }

        if (threadCache != null) {
            // 归还给线程缓存（快速路径）
            threadCache.recycle(buf, chunk, chunkOffset, normCapacity);
        } else if (chunk != null && chunk.parent != null) {
            // 无线程缓存，直接归还给 Arena（慢速路径）
            chunk.parent.free(chunk, chunkOffset, normCapacity);
        }
    }

    // ======================== 状态查询 ========================

    /**
     * 检查缓冲区是否仍被保留（refCount > 1）。
     * <p>
     * 注意：refCount == 1 不算"被保留"，因为这是从池中获取后的正常状态。
     * 只有 refCount > 1 才说明有额外的共享引用。
     * </p>
     *
     * @return true 如果 refCount > 1
     */
    public boolean isRetained() {
        return refCount.get() > 1;
    }

    /**
     * 获取当前引用计数。
     *
     * @return 引用计数
     */
    public int refCount() {
        return refCount.get();
    }

    /**
     * 获取最后一次更新时间戳（纳秒）。
     *
     * @return 最后更新时间的纳秒值
     */
    public long getLastUpdate() {
        return lastUpdateTime.get();
    }

    // ======================== 内部 API ========================

    /**
     * 内部获取方法：将引用计数从 0 设为 1（池内使用）。
     * 与 {@link #activate()} 相同，用于兼容 PoolThreadCache 的缓存复用场景。
     */
    void acquire() {
        activate();
    }

    @Override
    public String toString() {
        ByteBuffer buf = getBuffer();
        String bufInfo = buf != null ? BufferUtil.toDetailString(buf) : "null";
        return String.format("PooledByteBuffer@%x{%s,ref=%d,norm=%d,chunk=%s}",
                System.identityHashCode(this),
                bufInfo,
                refCount.get(),
                normCapacity,
                chunk != null ? "pooled" : "huge");
    }
}
