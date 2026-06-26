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
package com.gettyio.expansion.handler.codec.http.response;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.http.HttpDecodeSerializer;

/**
 * HTTP 响应解码器。
 * <p>
 * 将接收到的字节数据累积并解析为 {@link HttpResponse} 对象。
 * 支持半包场景，数据不完整时等待更多数据。
 * </p>
 *
 * @author gogym
 */
public class HttpResponseDecoder extends ByteToMessageDecoder {

    /** 接收数据缓冲区 */
    private final AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    /** 当前正在解析的响应对象 */
    private HttpResponse httpResponse;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        autoByteBuffer.writeBytes(bytes);

        if (httpResponse == null) {
            httpResponse = new HttpResponse();
            httpResponse.setReadStatus(HttpDecodeSerializer.READ_LINE);
        }


        boolean flag = HttpDecodeSerializer.read(autoByteBuffer, httpResponse);
        if (flag) {
            super.channelRead(ctx, httpResponse);
            autoByteBuffer.clear();
            httpResponse = null;
        }
    }
}
