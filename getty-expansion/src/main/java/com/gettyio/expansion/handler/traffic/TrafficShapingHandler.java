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
package com.gettyio.expansion.handler.traffic;

/**
 * 流量统计回调接口。
 * <p>
 * 配合 {@link ChannelTrafficShapingHandler} 使用，在定时周期内被调用，
 * 报告累计读写字节数和当前周期内的吞吐量。
 * </p>
 *
 * @author gogym
 */
public interface TrafficShapingHandler {

    /**
     * 流量统计回调。
     *
     * @param totalRead          累计读取字节数
     * @param totalWrite         累计写出字节数
     * @param intervalTotalRead  当前周期内读取字节数
     * @param intervalTotalWrite 当前周期内写出字节数
     * @param totalReadCount     累计读取次数
     * @param totalWriteCount    累计写出次数
     */
    void callback(long totalRead, long totalWrite,
                  long intervalTotalRead, long intervalTotalWrite,
                  long totalReadCount, long totalWriteCount);
}
