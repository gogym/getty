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
package com.gettyio.core.util.detector;


/**
 * ResourceLeakHint.java
 *
 * @description:一个提示对象，它提供可读的消息，以便更容易地跟踪资源泄漏。
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public interface ResourceLeakHint {
    /**
     * Returns a human-readable message that potentially enables easier resource leak tracking.
     * 返回可读的消息，使资源泄漏跟踪更容易。
     *
     * @return
     */
    String toHintString();
}
