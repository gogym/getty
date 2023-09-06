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
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.http.HttpDecodeSerializer;

/**
 * HttpResponseDecoder.java
 *
 * @description:http返回值解码
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class HttpResponseDecoder extends ByteToMessageDecoder {

    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    HttpResponse httpResponse;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {

        autoByteBuffer.writeBytes((byte[]) in);

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
