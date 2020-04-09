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
package com.gettyio.core.handler.ipfilter;

import java.net.InetSocketAddress;
import java.util.List;

import com.gettyio.core.channel.SocketChannel;

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
    protected boolean accept(SocketChannel socketChannel, InetSocketAddress remoteAddress) {

        if (rules.matches(remoteAddress)) {
            return rules.ruleType() == IpFilterRuleType.ACCEPT;
        }
        return true;
    }
}
