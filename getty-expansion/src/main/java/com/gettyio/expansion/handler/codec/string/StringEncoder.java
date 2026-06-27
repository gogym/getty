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
package com.gettyio.expansion.handler.codec.string;

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.util.CharsetUtil;

/**
 * 字符串编码器。
 * <p>
 * 将 {@link String} 或 byte[] 转换为 {@link PooledByteBuffer}，传递给下一个处理器。
 * 已经是 {@link PooledByteBuffer} 的数据直接透传。
 * </p>
 *
 * @author gogym
 * @see StringDecoder
 */
public class StringEncoder extends MessageToByteEncoder {

    @Override
    public void channelWrite(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof String) {
            // 方案二：编码器直出 byte[]，不分配 PooledByteBuffer
            // PooledByteBuffer 由写线程分配，确保分配和释放在同一线程
            obj = ((String) obj).getBytes(CharsetUtil.UTF_8);
        }
        super.channelWrite(ctx, obj);
    }
}
