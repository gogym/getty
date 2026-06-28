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
package com.gettyio.expansion.handler.codec.datagramPacket;

import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;

import java.net.DatagramPacket;

/**
 * UDP 数据包编码器。
 * <p>
 * 将非 DatagramPacket 类型的消息自动包装为 DatagramPacket。
 * 如果输入已是 DatagramPacket，则直接透传。
 * </p>
 */
public class DatagramPacketEncoder extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof DatagramPacket) {
            // 已是 DatagramPacket，直接透传
            super.channelWrite(ctx, obj);
        } else {
            throw new IllegalArgumentException("DatagramPacketEncoder only support DatagramPacket");
        }
    }
}
