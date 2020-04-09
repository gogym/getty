/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.util.detector;


/**
 * Level.java
 *
 * @description:表示资源泄漏检测的级别
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
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
