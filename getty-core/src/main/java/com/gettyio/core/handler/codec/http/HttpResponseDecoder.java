/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.handler.codec.http;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.util.LinkedNonReadBlockQueue;

/**
 * HttpResponseDecoder.java
 *
 * @description:http返回值解码
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class HttpResponseDecoder extends ObjectToMessageDecoder {

    AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer();
    HttpResponse httpResponse;

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {

        autoByteBuffer.writeBytes((byte[]) obj);

        if (httpResponse == null) {
            httpResponse = new HttpResponse();
            httpResponse.setReadStatus(HttpDecodeSerializer.READLINE);
        }

        if (httpResponse.getReadStatus() == HttpDecodeSerializer.READLINE) {
            if (!HttpDecodeSerializer.readResponseLine(autoByteBuffer, httpResponse)) {
                return;
            }
            httpResponse.setReadStatus(HttpDecodeSerializer.READHEADERS);
        }

        if (httpResponse.getReadStatus() == HttpDecodeSerializer.READHEADERS) {
            if (!HttpDecodeSerializer.readHeaders(autoByteBuffer, httpResponse)) {
                return;
            }
            httpResponse.setReadStatus(HttpDecodeSerializer.READCONTENT);
        }


        if (httpResponse.getReadStatus() == HttpDecodeSerializer.READCONTENT) {
            if (!HttpDecodeSerializer.readContent(autoByteBuffer, httpResponse)) {
                return;
            }
        }
        super.decode(socketChannel, httpResponse, out);
        autoByteBuffer.clear();
        httpResponse = null;
    }
}
