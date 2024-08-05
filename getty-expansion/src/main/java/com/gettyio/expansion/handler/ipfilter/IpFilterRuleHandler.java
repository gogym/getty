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

import com.gettyio.core.channel.AbstractSocketChannel;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * IpFilterRuleHandler.java
 *
 * @description:ip过滤器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class IpFilterRuleHandler extends AbstractRemoteAddressFilter<InetSocketAddress> {

    IpFilterRule rules;

    public IpFilterRuleHandler(List<IpRange> ips, IpFilterRuleType ipFilterRuleType) {
        if (ips == null) {
            throw new NullPointerException("rules");
        }
        rules = new RuleBasedIpFilter(ips, ipFilterRuleType);
    }


    @Override
    protected boolean accept(AbstractSocketChannel abstractSocketChannel, InetSocketAddress remoteAddress) {

        if (rules.matches(remoteAddress)) {
            return rules.ruleType() == IpFilterRuleType.ACCEPT;
        }
        return true;
    }
}
