package com.gettyio.core.buffer;

/**
 * 需要显式重分配的引用计数对象。
 * <p>
 * 当一个新的 {@link ReferenceCounted}被实例化时，它从{@code 1}.的引用计数开始。
 * {@link #retain()} 增加引用计数 {@link #release()} 减少引用计数。
 * 如果引用计数减少到 {@code 0}, 对象将被显式释放, 并且访问被释放的对象通常会导致访问冲突。
 * </p>
 * 如果一个实现 {@link ReferenceCounted} 的对象是其他实现{@link ReferenceCounted}的对象的容器 {@link #release()}，其所包含的对象也将通过
 * 释放引用计数变为0。
 * </p>
 */
public interface ReferenceCounted {
    /**
     * 返回此对象的引用计数。如果{@code 0}，则表示该对象已被释放。
     */
    int refCnt();

    /**
     * 将引用计数增加{@code 1}。
     */
    ReferenceCounted retain();

    /**
     * 按指定的{@code increment}增加到引用计数。
     */
    ReferenceCounted retain(int increment);

    /**
     * 记录此对象的当前访问位置，以便调试。
     */
    ReferenceCounted touch();

    /**
     * 用附加的用于调试的任意信息记录此对象的当前访问位置
     */
    ReferenceCounted touch(Object hint);

    /**
     * 通过{@code 1}减少引用计数并在引用计数达到{@code 0}时释放该对象
     *
     * @return {@code true} 当且仅当引用计数变为{@code 0}且该对象已被释放时
     */
    boolean release();

    /**
     * 根据指定的{@code 递减}减少引用计数，如果引用count达到{@code 0}。
     *
     * @return {@code true} 当且仅当引用计数变为{@code 0}且该对象已被释放时
     */
    boolean release(int decrement);
}
