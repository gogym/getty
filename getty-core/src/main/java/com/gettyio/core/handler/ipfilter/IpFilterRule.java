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


/**
 * IpFilterRule.java
 *
 * @description:ip过滤规则
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface IpFilterRule {

    /**
     * 对比地址
     *
     * @param remoteAddress 远程地址
     * @return This method should return true if remoteAddress is valid according to your criteria. False otherwise.
     */
    boolean matches(InetSocketAddress remoteAddress);

    /**
     * @return This method should return {@link IpFilterRuleType#ACCEPT} if all
     * {@link IpFilterRule#matches(InetSocketAddress)} for which {@link #matches(InetSocketAddress)}
     * returns true should the accepted. If you want to exclude all of those IP addresses then
     * {@link IpFilterRuleType#REJECT} should be returned.
     */
    IpFilterRuleType ruleType();
}
