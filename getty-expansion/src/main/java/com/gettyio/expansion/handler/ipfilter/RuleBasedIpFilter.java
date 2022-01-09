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

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.NetWorkUtil;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * RuleBasedIpFilter.java
 *
 * @description:Ip规则过滤器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 * @see RuleBasedIpFilter
 */
class RuleBasedIpFilter implements IpFilterRule {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RuleBasedIpFilter.class);

    List<IpRange> ips;
    IpFilterRuleType ipFilterRuleType;

    public RuleBasedIpFilter(List<IpRange> ips, IpFilterRuleType ipFilterRuleType) {
        if (ips == null) {
            logger.warn("blackIps was null");
        }
        this.ips = ips;
        this.ipFilterRuleType = ipFilterRuleType;
    }


    @Override
    public boolean matches(InetSocketAddress remoteAddress) {

        if (ips == null) {
            return true;
        }
        // ip转成long类型
        String ip = remoteAddress.getHostString();
        long ipLong = NetWorkUtil.ipToLong(ip);

        for (IpRange ipRange : ips) {
            long ipStart = NetWorkUtil.ipToLong(ipRange.getIpStart());
            long ipEnd = NetWorkUtil.ipToLong(ipRange.getIpEnd());
            // 比较ip区间
            if (ipLong >= ipStart && ipLong <= ipEnd) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IpFilterRuleType ruleType() {
        // 返回拒绝则表示拒绝连接，返回接受则表示可以连接
        return ipFilterRuleType;
    }

}
