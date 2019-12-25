package com.gettyio.core.handler.codec.protobuf;/*
 * 类名：ProtobufDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;


/**
 * 类名：ProtobufDecoder.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/9
 */
public class ProtobufDecoder extends ObjectToMessageDecoder {


    private static final boolean HAS_PARSER;

    static {
        boolean hasParser = false;
        try {
            // MessageLite.getParserForType() is not available until protobuf 2.5.0.
            MessageLite.class.getDeclaredMethod("getParserForType");
            hasParser = true;
        } catch (Throwable t) {
            // Ignore
        }

        HAS_PARSER = hasParser;
    }

    private final MessageLite prototype;
    private final ExtensionRegistryLite extensionRegistry;


    public ProtobufDecoder(MessageLite prototype) {
        this(prototype, null);
    }

    public ProtobufDecoder(MessageLite prototype, ExtensionRegistry extensionRegistry) {
        this(prototype, (ExtensionRegistryLite) extensionRegistry);
    }

    public ProtobufDecoder(MessageLite prototype, ExtensionRegistryLite extensionRegistry) {
        if (prototype == null) {
            throw new NullPointerException("prototype");
        }
        this.prototype = prototype.getDefaultInstanceForType();
        this.extensionRegistry = extensionRegistry;
    }


    @Override
    public void decode(AioChannel aioChannel, Object obj)  throws Exception{

        byte[] bytes = (byte[]) obj;

        final byte[] array;
        final int offset;

        AutoByteBuffer msg = AutoByteBuffer.newByteBuffer(bytes.length);
        msg.writeBytes(bytes);
        final int length = msg.readableBytes();
        if (msg.hasRemaining()) {
            array = msg.array();
            offset = msg.readerIndex();
        } else {
            return;
        }

        MessageLite messageLite = null;
        if (extensionRegistry == null) {
            try {
                if (HAS_PARSER) {
                    messageLite = prototype.getParserForType().parseFrom(array, offset, length);
                } else {
                    messageLite = prototype.newBuilderForType().mergeFrom(array, offset, length).build();
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

        } else {

            try {
                if (HAS_PARSER) {
                    messageLite = prototype.getParserForType().parseFrom(
                            array, offset, length, extensionRegistry);
                } else {
                    messageLite = prototype.newBuilderForType().mergeFrom(
                            array, offset, length, extensionRegistry).build();
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
        channelRead(aioChannel, messageLite);
        msg = null;
    }
}
