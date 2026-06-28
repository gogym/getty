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

import java.util.Collections;
import java.util.List;

/**
 * MQTT SUBSCRIBE 消息的负载部分。
 * <p>包含订阅的主题过滤器和对应的 QoS 级别列表。</p>
 *
 * @see MqttSubscribeMessage
 */
public final class MqttSubscribePayload {

    private final List<MqttTopicSubscription> topicSubscriptions;

    public MqttSubscribePayload(List<MqttTopicSubscription> topicSubscriptions) {
        this.topicSubscriptions = Collections.unmodifiableList(topicSubscriptions);
    }

    public List<MqttTopicSubscription> topicSubscriptions() {
        return topicSubscriptions;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(ObjectUtil.simpleClassName(this)).append('[');
        int size = topicSubscriptions.size();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(topicSubscriptions.get(i));
        }
        builder.append(']');
        return builder.toString();
    }
}
