package com.gettyio.core.buffer.pool;


import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.thread.AutoLock;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 对象池
 *
 * @param <T>
 */
public class Pool<T> implements AutoCloseable {

    private static final InternalLogger Logger = InternalLoggerFactory.getInstance(Pool.class);


    // 使用CopyOnWriteArrayList作为entries的存储方式，确保线程安全的读写访问
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    // 存储的最大条目数量
    private final int maxEntries;
    // 使用的策略类型，定义了处理条目的特定逻辑
    private final StrategyType strategyType;


    // AutoLock的实例，用于实现线程安全
    private final AutoLock lock = new AutoLock();
    // ThreadLocal实例，用于缓存Entry对象，确保每个线程都有自己的缓存副本，避免线程间干扰
    private final ThreadLocal<Entry> cache;
    // AtomicInteger用于安全地更新下一个索引值，保证多线程环境下的线程安全
    private final AtomicInteger nextIndex;
    // volatile标记的布尔变量，用于标记当前实例是否已被关闭，确保在多线程环境下的可见性
    private volatile boolean closed;

    /**
     * 池使用的策略类型
     */
    public enum StrategyType {
        /**
         * 从第一个条目开始寻找
         */
        FIRST,

        /**
         * 随机策略，返回一个范围在[0, size)内的随机数
         */
        RANDOM,

        /**
         * 线程ID策略，根据当前线程的ID取模以得到索引
         */
        THREAD_ID,

        /**
         * 轮询策略，维护一个全局索引并循环递增
         */
        ROUND_ROBIN
    }

    /**
     * 使用指定的查找策略构造一个Pool
     *
     * @param strategyType 用于查找条目的策略.
     * @param maxEntries   池接受的最大条目数量.
     */
    public Pool(StrategyType strategyType, int maxEntries) {
        this(strategyType, maxEntries, false);
    }

    /**
     * 用指定的线程本地缓存大小和一个可选的{@link ThreadLocal}缓存构造一个池。
     *
     * @param strategyType 用于查找条目的策略.
     * @param maxEntries   池接受的最大条目数量.
     * @param cache        使用{@link ThreadLocal}缓存来尝试最近释放的条目，则为True
     */
    public Pool(StrategyType strategyType, int maxEntries, boolean cache) {
        this.maxEntries = maxEntries;
        this.strategyType = strategyType;
        this.cache = cache ? new ThreadLocal<>() : null;
        this.nextIndex = strategyType == StrategyType.ROUND_ROBIN ? new AtomicInteger() : null;
    }

    /**
     * @return 返回池中保留的数量
     */
    public int getReservedCount() {
        return (int) entries.stream().filter(Entry::isReserved).count();
    }

    /**
     * @return 空闲的数目
     */
    public int getIdleCount() {
        return (int) entries.stream().filter(Entry::isIdle).count();
    }

    /**
     * @return 正在使用的数量
     */
    public int getInUseCount() {
        return (int) entries.stream().filter(Entry::isInUse).count();
    }

    /**
     * @return 关闭的数量
     */
    public int getClosedCount() {
        return (int) entries.stream().filter(Entry::isClosed).count();
    }

    /**
     * @return 池最大容纳数量
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 关闭任意可关闭对象，并在忽略异常
     *
     * @param closeable
     */
    public static void close(Closeable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (IOException ignore) {
            Logger.trace("IGNORED", ignore);
        }
    }

    /**
     * 在池中创建一个新的对象
     */
    public Entry reserve() {
        try (AutoLock l = lock.lock()) {
            if (closed) {
                return null;
            }

            // 如果没有空间返回null
            if (maxEntries > 0 && entries.size() >= maxEntries) {
                return null;
            }

            Entry entry = newEntry();
            entries.add(entry);
            return entry;
        }
    }

    /**
     * 创建一个新的Entry实例。
     * <p>这个方法负责创建并返回一个MonoEntry类型的Entry实例。</p>
     *
     * @return 返回一个新的、未启用的Entry实例。
     */
    private Entry newEntry() {
        return new MonoEntry();
    }

    /**
     * 从池中获取一个可用的条目。
     * 此方法仅返回已启用的条目，如果池已关闭或没有可用的条目，则返回null。
     *
     * @return 返回一个从池中获取的条目，如果无可用条目或池已关闭，则返回null。
     */
    public Entry acquire() {
        // 检查池是否已关闭，若已关闭，则直接返回null
        if (closed) {
            return null;
        }

        // 获取池中条目的数量
        int size = entries.size();
        // 如果池为空，则直接返回null
        if (size == 0) {
            return null;
        }

        // 尝试从缓存中获取一个条目，如果成功，并且该条目可以被获取，则返回该条目
        if (cache != null) {
            Entry entry = cache.get();
            if (entry != null && entry.tryAcquire()) {
                return entry;
            }
        }

        // 计算从池中获取条目的起始索引
        int index = startIndex(size);

        // 尝试遍历池中的条目，寻找一个可以被获取的条目
        for (int tries = size; tries-- > 0; ) {
            try {
                // 尝试获取索引位置的条目，并检查是否可以被获取
                Entry entry = entries.get(index);
                if (entry != null && entry.tryAcquire()) {
                    return entry;
                }
            } catch (IndexOutOfBoundsException e) {
                // 忽略索引越界的异常，可能是由于在获取条目的同时有其他线程移除了池中的最后一条条目
                Logger.trace("IGNORED", e);
                size = entries.size();
                // 如果在异常发生后，池中的条目数量为0，则结束循环
                if (size == 0) {
                    break;
                }
            }
            // 更新索引，以便遍历下一个条目
            index = (index + 1) % size;
        }
        // 如果遍历完所有条目后仍未找到可获取的条目，则返回null
        return null;
    }

    /**
     * 根据指定的策略类型计算起始索引。
     *
     * @param size 目标集合的大小，用于计算起始索引。
     * @return 返回根据策略类型计算得到的起始索引值。
     * @throws IllegalArgumentException 当传入的策略类型未知时抛出。
     */
    private int startIndex(int size) {
        switch (strategyType) {
            case FIRST:
                return 0;
            case RANDOM:
                // 随机策略，返回一个范围在[0, size)内的随机数
                return ThreadLocalRandom.current().nextInt(size);
            case ROUND_ROBIN:
                // 轮询策略，维护一个全局索引并循环递增，然后取模以保证在范围[0, size)内
                return nextIndex.getAndUpdate(c -> Math.max(0, c + 1)) % size;
            case THREAD_ID:
                // 线程ID策略，根据当前线程的ID取模以得到索引
                return (int) (Thread.currentThread().getId() % size);
            default:
                // 未知策略类型，抛出异常
                throw new IllegalArgumentException("Unknown strategy type: " + strategyType);
        }
    }

    /**
     * 从池中获取一个条目，如果必要，会创建一个新的条目并加以保留。
     *
     * @param creator 一个函数，用于为保留的条目创建池化的值。
     * @return 池中的一个条目，如果没有可用的条目则返回null。
     */
    public Entry acquire(Function<Entry, T> creator) {
        // 尝试直接从池中获取一个可用的条目
        Entry entry = acquire();
        if (entry != null) {
            return entry;
        }

        // 尝试保留一个条目
        entry = reserve();
        if (entry == null) {
            return null;
        }

        T value;
        try {
            // 使用提供的函数创建条目的值
            value = creator.apply(entry);
        } catch (Throwable th) {
            // 如果创建过程中发生异常，则移除该条目并抛出异常
            remove(entry);
            throw th;
        }

        // 如果创建的值为null，则移除条目并返回null
        if (value == null) {
            remove(entry);
            return null;
        }

        // 启用条目并返回，如果启用失败则返回null
        return entry.enable(value, true) ? entry : null;
    }


    /**
     * 将一个已获取的条目释放回池中。
     * <p>从池中获取但从未释放的条目将导致内存泄漏。</p>
     *
     * @param entry 要返回到池中的条目。
     * @return 如果条目被成功释放且可以再次获取，则返回true；如果条目应该通过调用{@link #remove(Entry)}移除，
     * 并且条目包含的对象应该被释放，则返回false。
     */
    public boolean release(Entry entry) {
        // 检查池是否已关闭，若已关闭，则不处理释放操作
        if (closed) {
            return false;
        }

        // 尝试释放条目
        boolean released = entry.tryRelease();
        // 如果释放成功且存在缓存机制，则将条目放入缓存中
        if (released && cache != null) {
            cache.set(entry);
        }
        return released;
    }


    /**
     * 从池中移除一个条目。
     *
     * @param entry 要移除的条目
     * @return 如果条目被成功移除，则返回true；否则返回false。
     */
    public boolean remove(Entry entry) {
        // 检查池是否已关闭，若已关闭，则不处理移除操作
        if (closed) {
            return false;
        }

        // 尝试从条目本身标记为移除，如果条目当前仍在使用中，则尝试移除操作失败
        if (!entry.tryRemove()) {
            // 当日志级别为DEBUG时，记录尝试移除仍在使用中的条目的信息
            if (Logger.isDebugEnabled()) {
                Logger.debug("Attempt to remove an object from the pool that is still in use: {}", entry);
            }
            return false;
        }

        // 从条目集合中尝试移除条目，如果移除失败且日志级别为DEBUG，记录相关信息
        boolean removed = entries.remove(entry);
        if (!removed && Logger.isDebugEnabled()) {
            Logger.debug("Attempt to remove an object from the pool that does not exist: {}", entry);
        }

        return removed;
    }


    /**
     * 检查对象是否已关闭。
     *
     * @return closed 返回一个布尔值，表示对象是否已关闭。如果对象已关闭，则返回true；否则返回false。
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * 关闭资源的方法。此方法将标记当前对象为已关闭，并清空内部条目列表。对于列表中的每个条目，
     * 尝试移除它们，并且如果移除成功，且条目的 pooled 对象实现了 Closeable 接口，则关闭该对象。
     * 如果条目无法被移除，可能因为它仍在使用中，将记录一条调试信息（如果调试模式开启）。
     */
    @Override
    public void close() {
        List<Entry> copy;
        // 尝试获取锁并安全地关闭资源
        try (AutoLock l = lock.lock()) {
            closed = true; // 标记为已关闭
            copy = new ArrayList<>(entries); // 复制当前条目列表到安全的副本
            entries.clear(); // 清空内部条目列表，以避免进一步的操作
        }

        // 遍历副本列表，尝试关闭每个条目
        for (Entry entry : copy) {
            boolean removed = entry.tryRemove(); // 尝试移除条目
            if (removed) {
                // 如果移除成功，检查 pooled 对象是否可关闭
                if (entry.pooled instanceof Closeable) {
                    close((Closeable) entry.pooled); // 安全关闭 pooled 对象
                }
            } else {
                // 如果无法移除，可能因为对象仍在使用中
                if (Logger.isDebugEnabled()) {
                    Logger.debug("Pooled object still in use: {}", entry); // 记录调试信息
                }
            }
        }
    }


    /**
     * 获取当前条目数量。
     * <p>此方法返回存储所有条目的集合的大小。</p>
     *
     * @return 返回集合中条目的数量。
     */
    public int size() {
        return entries.size();
    }


    /**
     * 获取所有条目的不可修改集合。
     * <p>此方法返回一个不可修改的视图，包含此对象中所有的条目。对返回集合的任何修改尝试都会导致抛出 {@link UnsupportedOperationException}。</p>
     *
     * @return 一个不可修改的条目集合视图。
     */
    public Collection<Entry> values() {
        return Collections.unmodifiableCollection(entries);
    }


    @Override
    public String toString() {
        return String.format("%s@%x[inUse=%d,size=%d,max=%d,closed=%b]",
                getClass().getSimpleName(),
                hashCode(),
                getInUseCount(),
                size(),
                getMaxEntries(),
                isClosed());
    }

    /**
     * 保存元数据的池对象
     */
    public abstract class Entry {

        private T pooled;

        /**
         * 启用之前通过 {@link #reserve() 预留} 的 Entry。
         * <p>从 {@link #reserve()} 方法返回的 Entry 必须使用此方法启用一次且仅一次，才能在池中使用。</p>
         * <p>Entry 可以被启用但未被获取，在这种情况下，它会立即可用，可能被另一个线程获取；或者可以被原子地启用和获取，以确保没有其他线程可以获取它，
         * 尽管如果池已被关闭，获取仍可能失败。</p>
         *
         * @param pooled  此 Entry 的池化对象
         * @param acquire 是否应原子地启用并获取此 Entry
         * @return 此 Entry 是否被启用
         * @throws IllegalStateException 如果此 Entry 已被启用
         */
        public boolean enable(T pooled, boolean acquire) {
            Objects.requireNonNull(pooled);  // 确保池化对象不为null

            if (!isReserved()) { // 检查是否已预留
                if (isClosed()) {
                    return false; // 如果池已关闭，则直接返回false
                }
                throw new IllegalStateException("Entry already enabled: " + this);
            }
            this.pooled = pooled;  // 设置池化对象

            if (tryEnable(acquire)) { // 尝试启用Entry
                return true;
            }

            this.pooled = null;  // 如果启用失败，则重置池化对象
            if (isClosed()) {
                return false; // 再次检查池状态，如果已关闭，则返回false
            }
            throw new IllegalStateException("Entry already enabled: " + this);
        }


        /**
         * 获取池化对象。
         * <p>该方法用于从池中获取一个对象。不接受任何参数，返回池中的对象。</p>
         *
         * @return 返回池化对象。类型为T，代表池中对象的类型。
         */
        public T getPooled() {
            return pooled;
        }

        /**
         * 释放此Entry。
         * <p>这等同于调用{@link Pool#release(Entry)}并传入此Entry。</p>
         *
         * @return 如果此Entry被释放，则返回true
         */
        public boolean release() {
            return Pool.this.release(this);
        }

        /**
         * 从Pool中移除此Entry。
         * <p>这等同于调用{@link Pool#remove(Entry)}并传入此Entry。</p>
         *
         * @return 如果此Entry被移除，则返回true
         */
        public boolean remove() {
            return Pool.this.remove(this);
        }

        /**
         * 尝试启用，如果指定，则同时获取此Entry。
         * <p>此方法是抽象的，具体行为由子类实现。</p>
         *
         * @param acquire 是否同时尝试获取此Entry
         * @return 如果此Entry被启用，则返回true
         */
        abstract boolean tryEnable(boolean acquire);

        /**
         * 尝试获取这个Entry。
         * <p>这是一个抽象方法，具体的行为由子类实现。</p>
         *
         * @return boolean 如果这个Entry被成功获取，则返回true；否则返回false。
         */
        abstract boolean tryAcquire();

        /**
         * 尝试释放这个Entry。
         * <p>释放Entry的具体行为由子类实现。如果释放成功，则返回true，否则建议调用者尝试调用{@link #tryRemove()}方法。</p>
         *
         * @return true如果这个Entry被成功释放，false如果应该调用{@link #tryRemove()}方法。
         */
        abstract boolean tryRelease();

        /**
         * 尝试通过标记为关闭来移除这个Entry。
         * <p>这是一个抽象方法，具体的行为由子类实现。成功移除后，该Entry可以从包含的池中移除。</p>
         *
         * @return boolean 如果这个Entry可以被从包含的池中移除，则返回true；否则返回false。
         */
        abstract boolean tryRemove();

        /**
         * 判断当前Entry是否已关闭。
         * 这是一个抽象方法，具体实现由子类定义。
         *
         * @return boolean 如果当前Entry已经关闭，则返回true；否则返回false。
         */
        public abstract boolean isClosed();

        /**
         * 判断当前Entry是否已预留。
         * 这是一个抽象方法，具体实现由子类定义。
         *
         * @return boolean 如果当前Entry已经预留，则返回true；否则返回false。
         */
        public abstract boolean isReserved();

        /**
         * 判断当前Entry是否为空闲状态。
         * 这是一个抽象方法，具体实现由子类定义。
         *
         * @return boolean 如果当前Entry处于空闲状态，则返回true；否则返回false。
         */
        public abstract boolean isIdle();

        /**
         * 判断当前对象是否正在被使用。
         * 这是一个抽象方法，需要在子类中具体实现。
         *
         * @return boolean 如果对象正在被使用，则返回true；否则返回false。
         */
        public abstract boolean isInUse();

        /**
         * 检查系统是否处于空闲状态且资源使用过度。
         *
         * @return 布尔值，如果系统既空闲又资源使用过度则返回true，否则返回false。
         */
        boolean isIdleAndOverUsed() {
            return false;
        }


        /**
         * 仅用于测试。
         * 获取使用计数。
         *
         * @return 当前的使用次数。
         */
        int getUsageCount() {
            return 0;
        }


        /**
         * 仅用于测试。
         * 设置使用计数。
         *
         * @param usageCount 使用次数。
         */
        void setUsageCount(int usageCount) {
        }

    }

    /**
     * 保存元数据和池对象的池项，最多只能并发获取一次，并且可以多次获取/释放
     */
    private class MonoEntry extends Entry {
        // MIN_VALUE => pending; -1 => closed; 0 => idle; 1 => active;
        private final AtomicInteger state = new AtomicInteger(Integer.MIN_VALUE);

        @Override
        protected boolean tryEnable(boolean acquire) {
            return state.compareAndSet(Integer.MIN_VALUE, acquire ? 1 : 0);
        }

        @Override
        boolean tryAcquire() {
            while (true) {
                int s = state.get();
                if (s != 0)
                    return false;
                if (state.compareAndSet(s, 1))
                    return true;
            }
        }

        @Override
        boolean tryRelease() {
            while (true) {
                int s = state.get();
                if (s < 0)
                    return false;
                if (s == 0)
                    throw new IllegalStateException("Cannot release an already released entry");
                if (state.compareAndSet(s, 0))
                    return true;
            }
        }

        @Override
        boolean tryRemove() {
            state.set(-1);
            return true;
        }

        @Override
        public boolean isClosed() {
            return state.get() < 0;
        }

        @Override
        public boolean isReserved() {
            return state.get() == Integer.MIN_VALUE;
        }

        @Override
        public boolean isIdle() {
            return state.get() == 0;
        }

        @Override
        public boolean isInUse() {
            return state.get() == 1;
        }

        @Override
        public String toString() {
            String s;
            switch (state.get()) {
                case Integer.MIN_VALUE:
                    s = "PENDING";
                    break;
                case -1:
                    s = "CLOSED";
                    break;
                case 0:
                    s = "IDLE";
                    break;
                default:
                    s = "ACTIVE";
            }
            return String.format("%s@%x{%s,pooled=%s}",
                    getClass().getSimpleName(),
                    hashCode(),
                    s,
                    getPooled());
        }
    }
}