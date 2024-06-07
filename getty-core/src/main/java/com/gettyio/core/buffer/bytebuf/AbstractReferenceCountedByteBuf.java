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
package com.gettyio.core.buffer.bytebuf;


import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 抽象ReferenceCountedByteBuf类是对ByteBuf接口的实现，它添加了引用计数的逻辑。
 * 这个类是抽象的，意味着它不提供具体的实现，而是为具体的实现提供了一个框架。
 * 引用计数机制用于管理资源的生命周期，确保当没有更多的引用指向一个对象时，对象可以被安全地释放。
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {


    /**
     * 使用AtomicIntegerFieldUpdater来安全地更新AbstractReferenceCountedByteBuf类中的refCnt字段。
     * 由于refCnt字段的访问和更新可能在多线程环境中发生，因此使用AtomicIntegerFieldUpdater确保了字段更新的原子性和线程安全性。
     * 通过这种方式，可以避免在高并发场景下对refCnt字段进行同步控制所带来的性能瓶颈。
     */
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> refCntUpdater;
    static {
        //创建并返回一个具有给定字段的更新器
        refCntUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");
    }

    /**
     * 需要计数的字段
     * 字段必须是 volatile 类型的，在线程之间共享变量时保证立即可见
     */
    private volatile int refCnt = 1;

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public final int refCnt() {
        return refCnt;
    }


    /**
     * 设置引用计数。
     * <p>
     * 该方法用于直接设置当前对象的引用计数。引用计数是一个重要的内部状态，用于管理对象的生命周期和共享状态。
     * 通过调整引用计数，可以决定对象是否应该被释放或者继续存在。
     *
     * @param refCnt 新的引用计数值。这个值被直接赋给当前对象的引用计数字段。
     */
    protected final void setRefCnt(int refCnt) {
        this.refCnt = refCnt;
    }


    @Override
    public ByteBuf retain() {
        return retain(1);
    }

    @Override
    public ByteBuf retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }

        for (; ; ) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, increment);
            }
            if (refCnt > Integer.MAX_VALUE - increment) {
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + increment)) {
                break;
            }
        }
        return this;
    }

    @Override
    public final boolean release() {
        return release(1);
    }

    @Override
    public final boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("decrement: " + decrement + " (expected: > 0)");
        }

        for (; ; ) {
            int refCnt = this.refCnt;
            if (refCnt < decrement) {
                throw new IllegalReferenceCountException(refCnt, -decrement);
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - decrement)) {
                if (refCnt == decrement) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }


    /**
     * 抽象方法，用于释放资源或进行清理工作。
     * 该方法被设计为由子类实现，以在对象不再需要时释放资源，降低内存泄漏的风险。
     * 具体的释放逻辑由子类根据实际需求来实现。
     * 注意，该方法没有返回值，它的目的是在调用后释放或清理对象所持有的资源。
     */
    protected abstract void deallocate();

}
