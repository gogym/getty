/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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

package com.gettyio.expansion.handler.codec.mqtt;


import com.gettyio.core.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MQTT SUBACK 消息的负载部分。
 * <p>包含服务端授予的 QoS 级别列表。</p>
 *
 * @see MqttSubAckMessage
 */
public class MqttSubAckPayload {

    private final List<Integer> grantedQoSLevels;

    /**
     * 通过可变参数创建 SUBACK 负载
     *
     * @param grantedQoSLevels 授予的 QoS 级别数组
     * @throws NullPointerException 参数为 null
     */
    public MqttSubAckPayload(int... grantedQoSLevels) {
        if (grantedQoSLevels == null) {
            throw new NullPointerException("grantedQoSLevels");
        }

        List<Integer> list = new ArrayList<Integer>(grantedQoSLevels.length);
        for (int v : grantedQoSLevels) {
            list.add(v);
        }
        this.grantedQoSLevels = Collections.unmodifiableList(list);
    }

    /**
     * 通过可迭代集合创建 SUBACK 负载
     *
     * @param grantedQoSLevels 授予的 QoS 级别集合
     * @throws NullPointerException 参数为 null
     */
    public MqttSubAckPayload(Iterable<Integer> grantedQoSLevels) {
        if (grantedQoSLevels == null) {
            throw new NullPointerException("grantedQoSLevels");
        }
        List<Integer> list = new ArrayList<Integer>();
        for (Integer v : grantedQoSLevels) {
            if (v == null) {
                continue;
            }
            list.add(v);
        }
        this.grantedQoSLevels = Collections.unmodifiableList(list);
    }

    public List<Integer> grantedQoSLevels() {
        return grantedQoSLevels;
    }

    @Override
    public String toString() {
        return new StringBuilder(ObjectUtil.simpleClassName(this))
            .append('[')
            .append("grantedQoSLevels=").append(grantedQoSLevels)
            .append(']')
            .toString();
    }
}
