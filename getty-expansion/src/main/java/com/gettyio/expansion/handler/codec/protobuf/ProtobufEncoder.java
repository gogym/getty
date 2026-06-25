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

import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.google.protobuf.MessageLite;

/**
 * Protobuf 消息编码器。
 * <p>
 * 将 {@link MessageLite} 或其 Builder 对象序列化为 byte[]，传递给下一个处理器。
 * 非 Protobuf 类型的数据将原样透传。
 * </p>
 *
 * @author gogym
 * @see ProtobufDecoder
 */
public class ProtobufEncoder extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof MessageLite) {
            byte[] bytes = ((MessageLite) obj).toByteArray();
            RetainableByteBuffer buf = ctx.channel().getByteBufferPool().acquire(bytes.length);
            buf.writeBytes(bytes);
            obj = buf;
        } else if (obj instanceof MessageLite.Builder) {
            byte[] bytes = ((MessageLite.Builder) obj).build().toByteArray();
            RetainableByteBuffer buf = ctx.channel().getByteBufferPool().acquire(bytes.length);
            buf.writeBytes(bytes);
            obj = buf;
        }
        super.channelWrite(ctx, obj);
    }
}
