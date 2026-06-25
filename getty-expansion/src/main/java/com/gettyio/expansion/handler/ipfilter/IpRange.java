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
package com.gettyio.expansion.handler.ipfilter;

/**
 * IP 地址段，表示一个从 {@code ipStart} 到 {@code ipEnd} 的闭区间。
 *
 * @author gogym
 */
class IpRange {

    private final String ipStart;
    private final String ipEnd;

    /**
     * 创建 IP 地址段。
     *
     * @param ipStart 起始 IP 地址（如 "192.168.0.1"）
     * @param ipEnd   结束 IP 地址（如 "192.168.0.255"）
     * @throws NullPointerException     如果 ipStart 或 ipEnd 为 null
     * @throws IllegalArgumentException 如果 ipStart 或 ipEnd 为空字符串
     */
    public IpRange(String ipStart, String ipEnd) {
        if (ipStart == null || ipEnd == null) {
            throw new NullPointerException("ipStart and ipEnd must not be null");
        }
        if (ipStart.isEmpty() || ipEnd.isEmpty()) {
            throw new IllegalArgumentException("ipStart and ipEnd must not be empty");
        }
        this.ipStart = ipStart;
        this.ipEnd = ipEnd;
    }

    public String getIpStart() {
        return ipStart;
    }

    public String getIpEnd() {
        return ipEnd;
    }
}
