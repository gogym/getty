package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/16
 */

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.util.LinkedNonBlockQueue;

public class HttpDecoder extends ObjectToMessageDecoder {

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        if (obj instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) obj;
            out.put(request);
        }
        super.decode(socketChannel, obj, out);
    }
}
