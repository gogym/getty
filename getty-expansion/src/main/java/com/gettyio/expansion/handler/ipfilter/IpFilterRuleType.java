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
 * IP 过滤规则类型。
 * <p>
 * 配合 {@link IpFilterRule} 使用，决定匹配的地址是被接受还是被拒绝。
 * </p>
 *
 * @author gogym
 */
enum IpFilterRuleType {
    /** 接受匹配地址 */
    ACCEPT,
    /** 拒绝匹配地址 */
    REJECT
}
