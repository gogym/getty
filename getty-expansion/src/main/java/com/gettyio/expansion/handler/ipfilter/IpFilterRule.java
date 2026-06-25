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

import java.net.InetSocketAddress;

/**
 * IP 过滤规则接口。
 * <p>
 * 定义了两个方法：{@link #matches(InetSocketAddress)} 判断地址是否匹配，
 * {@link #ruleType()} 返回匹配结果的处理策略。
 * </p>
 *
 * @author gogym
 */
public interface IpFilterRule {

    /**
     * 判断远程地址是否匹配此规则。
     *
     * @param remoteAddress 远程地址
     * @return true 表示匹配
     */
    boolean matches(InetSocketAddress remoteAddress);

    /**
     * 返回匹配结果的处理策略。
     *
     * @return {@link IpFilterRuleType#ACCEPT} 表示匹配地址允许连接，
     *         {@link IpFilterRuleType#REJECT} 表示匹配地址拒绝连接
     */
    IpFilterRuleType ruleType();
}
