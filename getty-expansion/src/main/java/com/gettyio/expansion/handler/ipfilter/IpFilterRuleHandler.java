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
 * IP 过滤规则处理器。
 * <p>
 * 根据预设的 IP 地址段列表和过滤策略（接受/拒绝），
 * 在新通道建立时检查远程地址是否符合规则，不符合则关闭连接。
 * </p>
 *
 * @author gogym
 * @see RuleBasedIpFilter
 */
public class IpFilterRuleHandler extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final IpFilterRule rules;

    /**
     * 创建 IP 过滤规则处理器。
     *
     * @param ips             IP 地址段列表
     * @param ipFilterRuleType 过滤策略：{@link IpFilterRuleType#ACCEPT} 接受匹配地址，
     *                         {@link IpFilterRuleType#REJECT} 拒绝匹配地址
     * @throws NullPointerException 如果 ips 为 null
     */
    public IpFilterRuleHandler(List<IpRange> ips, IpFilterRuleType ipFilterRuleType) {
        if (ips == null) {
            throw new NullPointerException("ips");
        }
        rules = new RuleBasedIpFilter(ips, ipFilterRuleType);
    }

    @Override
    protected boolean accept(AbstractSocketChannel abstractSocketChannel, InetSocketAddress remoteAddress) {
        if (rules.matches(remoteAddress)) {
            return rules.ruleType() == IpFilterRuleType.ACCEPT;
        }
        // 不在规则列表中的地址，默认放行
        return true;
    }
}
