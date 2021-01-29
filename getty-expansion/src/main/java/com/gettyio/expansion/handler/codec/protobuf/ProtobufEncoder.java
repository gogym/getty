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
package com.gettyio.expansion.handler.codec.protobuf;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.google.protobuf.MessageLite;


/**
 * ProtobufEncoder.java
 *
 * @description:protobuf编码
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class ProtobufEncoder extends MessageToByteEncoder {

    @Override
    public void encode(SocketChannel socketChannel, Object obj) throws Exception {

        byte[] bytes = null;
        if (obj instanceof MessageLite) {
            bytes = ((MessageLite) obj).toByteArray();
        }
        if (obj instanceof MessageLite.Builder) {
            bytes = ((MessageLite.Builder) obj).build().toByteArray();
        }
        super.encode(socketChannel, bytes);
    }
}
