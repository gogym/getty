package com.gettyio.core.util.detector;

/**
 * 类名：ResourceLeakHint.java
 * 描述：A hint object that provides human-readable message for easier resource leak tracking.
 * 一个提示对象，它提供可读的消息，以便更容易地跟踪资源泄漏。
 * 修改人：gogym
 * 时间：2020/3/17
 * 参考：netty 4.3
 */
public interface ResourceLeakHint {
    /**
     * Returns a human-readable message that potentially enables easier resource leak tracking.
     * 返回可读的消息，使资源泄漏跟踪更容易。
     */
    String toHintString();
}
