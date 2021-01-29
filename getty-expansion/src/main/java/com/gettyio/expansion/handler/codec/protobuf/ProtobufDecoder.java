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

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.util.LinkedNonReadBlockQueue;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;


/**
 * ProtobufDecoder.java
 *
 * @description:protobuf解码
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
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
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonReadBlockQueue<Object> out) throws Exception {

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
        out.put(messageLite);
        super.decode(socketChannel, obj, out);
    }
}
