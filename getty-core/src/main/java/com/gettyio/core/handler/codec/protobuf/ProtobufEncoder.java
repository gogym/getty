package com.gettyio.core.handler.codec.protobuf;/*
 * 类名：ProtobufEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/8
 */

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.google.protobuf.MessageLite;

/**
 * 类名：ProtobufEncoder.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/9
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
