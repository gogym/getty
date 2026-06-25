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

import com.gettyio.core.util.NetWorkUtil;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 基于 IP 地址段的过滤规则实现。
 * <p>
 * 在构造时预计算所有 IP 段的 long 值，避免每次匹配时重复转换，提升性能。
 * </p>
 *
 * @author gogym
 * @see IpFilterRuleHandler
 */
class RuleBasedIpFilter implements IpFilterRule {

    private final List<IpRange> ipRanges;
    private final IpFilterRuleType ipFilterRuleType;

    /** 预计算的 IP 段 long 值数组，避免每次匹配时重复调用 ipToLong */
    private final long[] ipStartLongs;
    private final long[] ipEndLongs;

    /**
     * 创建基于规则的 IP 过滤器。
     *
     * @param ips             IP 地址段列表
     * @param ipFilterRuleType 过滤策略
     */
    public RuleBasedIpFilter(List<IpRange> ips, IpFilterRuleType ipFilterRuleType) {
        this.ipRanges = ips;
        this.ipFilterRuleType = ipFilterRuleType;

        if (ips != null) {
            ipStartLongs = new long[ips.size()];
            ipEndLongs = new long[ips.size()];
            for (int i = 0; i < ips.size(); i++) {
                ipStartLongs[i] = NetWorkUtil.ipToLong(ips.get(i).getIpStart());
                ipEndLongs[i] = NetWorkUtil.ipToLong(ips.get(i).getIpEnd());
            }
        } else {
            ipStartLongs = null;
            ipEndLongs = null;
        }
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        if (ipRanges == null) {
            return true;
        }
        long ipLong = NetWorkUtil.ipToLong(remoteAddress.getHostString());
        for (int i = 0; i < ipStartLongs.length; i++) {
            if (ipLong >= ipStartLongs[i] && ipLong <= ipEndLongs[i]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IpFilterRuleType ruleType() {
        return ipFilterRuleType;
    }
}
