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
package com.gettyio.expansion.handler.codec.http.request;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.http.HttpDecodeSerializer;
import com.gettyio.expansion.handler.codec.http.HttpHeaders;

/**
 * HTTP 请求解码器。
 * <p>
 * 将接收到的字节数据累积并解析为 {@link HttpRequest} 对象。
 * 支持半包场景，数据不完整时等待更多数据。
 * </p>
 *
 * @author gogym
 */
public class HttpRequestDecoder extends ByteToMessageDecoder {

    /** 接收数据缓冲区 */
    private final AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    /** 当前正在解析的请求对象 */
    private HttpRequest httpRequest;
    /** 解析状态（由 Decoder 持有，不属于 HttpMessage） */
    private final HttpDecodeSerializer.ParseState parseState = new HttpDecodeSerializer.ParseState();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        autoByteBuffer.writeBytes(bytes);

        if (httpRequest == null) {
            httpRequest = new HttpRequest();
            parseState.reset();
        }

        boolean flag = HttpDecodeSerializer.read(autoByteBuffer, httpRequest, parseState);
        if (flag) {
            // 根据请求的 Connection 头部自动设置通道的 keepAlive 状态
            ctx.channel().setKeepAlive(HttpHeaders.isKeepAlive(httpRequest));
            super.channelRead(ctx, httpRequest);
            autoByteBuffer.clear();
            httpRequest = null;
        }
    }
}
