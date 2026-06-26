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

import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

/**
 * Protobuf 消息解码器。
 * <p>
 * 将 byte[] 数据解码为 Protobuf {@link MessageLite} 对象。
 * 支持 Protobuf 2.5.0+ 的 Parser API，对低版本使用 Builder API 兼容。
 * </p>
 *
 * @author gogym
 * @see ProtobufEncoder
 */
public class ProtobufDecoder extends ByteToMessageDecoder {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ProtobufDecoder.class);

    /** 是否支持 getParserForType()（protobuf 2.5.0+） */
    private static final boolean HAS_PARSER;

    static {
        boolean hasParser = false;
        try {
            MessageLite.class.getDeclaredMethod("getParserForType");
            hasParser = true;
        } catch (Throwable t) {
            // protobuf < 2.5.0，忽略
        }
        HAS_PARSER = hasParser;
    }

    private final MessageLite prototype;
    private final ExtensionRegistryLite extensionRegistry;

    public ProtobufDecoder(MessageLite prototype) {
        this(prototype, (ExtensionRegistryLite) null);
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
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (bytes.length == 0) {
            return;
        }

        MessageLite messageLite;
        try {
            if (extensionRegistry == null) {
                if (HAS_PARSER) {
                    messageLite = prototype.getParserForType().parseFrom(bytes);
                } else {
                    messageLite = prototype.newBuilderForType().mergeFrom(bytes).build();
                }
            } else {
                if (HAS_PARSER) {
                    messageLite = prototype.getParserForType().parseFrom(bytes, extensionRegistry);
                } else {
                    messageLite = prototype.newBuilderForType().mergeFrom(bytes, extensionRegistry).build();
                }
            }
        } catch (InvalidProtocolBufferException e) {
            LOGGER.error("protobuf decode failed", e);
            return;
        }
        super.channelRead(ctx, messageLite);
    }
}
