package com.gettyio.core.buffer.pool;




/**
 * Retainable 接口定义了一个对象被保留（或增加引用计数）的行为。
 * 该接口仅包含一个方法 retain()，用于增加对象的引用计数。
 */
public interface Retainable {
    /**
     * retain 方法用于保留当前对象，即增加对象的引用计数。
     * 该方法没有参数和返回值，其主要作用是影响对象的生命周期，
     * 通常用于对象池或引用计数管理的场景中。
     */
    void retain();
}
