
package com.gettyio.core.util.detector;

/**
 * 类名：ResourceLeakTracker.java
 * 描述：内存检测接口
 * 修改人：gogym
 * 时间：2020/3/17
 * 参考：netty 4.3
 */
public interface ResourceLeakTracker<T> {

    /**
     * Records the caller's current stack trace so that the {@link ResourceLeakDetector} can tell where the leaked
     * resource was accessed lastly. This method is a shortcut to {@link #record(Object) record(null)}.
     * 记录调用者的当前堆栈跟踪，这样{@link ResourceLeakDetector}就可以告诉泄漏的资源最后是在什么地方被访问的。
     * 这个方法是{@link #record(Object) record(null)}的快捷方式。
     */
    void record();

    /**
     * Records the caller's current stack trace and the specified additional arbitrary information
     * so that the {@link ResourceLeakDetector} can tell where the leaked resource was accessed lastly.
     * 记录调用方的当前堆栈跟踪和指定的其他任意信息
     * 这样{@link ResourceLeakDetector}就可以告诉泄漏的资源最后是在什么地方被访问的。
     */
    void record(Object hint);

    /**
     * Close the leak so that {@link ResourceLeakTracker} does not warn about leaked resources.
     * After this method is called a leak associated with this ResourceLeakTracker should not be reported.
     * 关闭泄漏，这样{@link ResourceLeakTracker}就不会对泄漏的资源发出警告。
     * 调用此方法后，不应报告与此ResourceLeakTracker关联的泄漏。
     *
     * @return {@code true} if called first time, {@code false} if called already
     */
    boolean close(T trackedObject);
}
