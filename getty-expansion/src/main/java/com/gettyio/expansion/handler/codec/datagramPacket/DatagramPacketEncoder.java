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
import com.gettyio.core.pipeline.all.DatagramPacketHandler;

/**
 * DatagramPacketEncoder.java
 *
 * @description:upd包编码器
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class DatagramPacketEncoder extends MessageToByteEncoder implements DatagramPacketHandler {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        //udp包直接由通道发出，实际这里并没有处理什么
        super.channelWrite(ctx,obj);
    }
}
