package com.gettyio.core.util.detector;

/**
 * Represents the level of resource leak detection.
 * 表示资源泄漏检测的级别
 */
public enum Level {

    /**
     * Disables resource leak detection.
     * 禁用资源泄漏检测。
     */
    DISABLED,
    /**
     * Enables simplistic sampling resource leak detection which reports there is a leak or not,
     * at the cost of small overhead (default).
     * 启用简单的抽样资源泄漏检测，报告是否存在泄漏，开销很小(默认值)。
     */
    SIMPLE,
    /**
     * Enables advanced sampling resource leak detection which reports where the leaked object was accessed
     * recently at the cost of high overhead.
     * 启用高级抽样资源泄漏检测，该检测报告最近在何处访问了泄漏的对象，但代价是较高的开销。
     */
    ADVANCED,
    /**
     * Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
     * at the cost of the highest possible overhead (for testing purposes only).
     * 启用偏执狂资源泄漏检测，它报告泄漏的对象最近在何处被访问，代价可能是最高的开销(仅用于测试目的)。
     */
    PARANOID;

    /**
     * Returns level based on string value. Accepts also string that represents ordinal number of enum.
     * 返回基于字符串值的级别。也接受表示枚举序数的字符串。
     *
     * @param levelStr - level string : DISABLED, SIMPLE, ADVANCED, PARANOID. Ignores case.
     * @return corresponding level or SIMPLE level in case of no match.
     */
    static Level parseLevel(String levelStr) {
        String trimmedLevelStr = levelStr.trim();
        for (Level l : values()) {
            if (trimmedLevelStr.equalsIgnoreCase(l.name()) || trimmedLevelStr.equals(String.valueOf(l.ordinal()))) {
                return l;
            }
        }
        return SIMPLE;
    }
}
