package com.gettyio.core.handler.codec.protobuf;/*
 * 类名：ProtobufVarint32FrameDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/8
 */

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.pipeline.PipelineDirection;

import java.io.IOException;


/**
 * 类名：ProtobufVarint32FrameDecoder.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/10/9
 */
public class ProtobufVarint32FrameDecoder extends ObjectToMessageDecoder {


    @Override
    public void handler(ChannelState channelStateEnum, Object obj, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        byte[] bytes = (byte[]) obj;
        if (channelStateEnum == ChannelState.CHANNEL_READ) {
            AutoByteBuffer autoByteBuffer = AutoByteBuffer.newByteBuffer(bytes.length);
            autoByteBuffer.writeBytes(bytes);
            try {

                while (autoByteBuffer.hasRemaining()) {

                    int preIndex = autoByteBuffer.readerIndex();
                    int length = readRawVarint32(autoByteBuffer);
                    if (preIndex == autoByteBuffer.readerIndex()) {
                        return;
                    }
                    if (length < 0) {
                        throw new RuntimeException("negative length: " + length);
                    }
                    if (autoByteBuffer.readableBytes() < length) {
                        autoByteBuffer.reset();
                    } else {
                        byte[] b = new byte[length];
                        autoByteBuffer.readBytes(b);
                        //解码
                        decode(aioChannel, b);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("protobuf decode error", e);
            }
        } else {
            super.handler(channelStateEnum, bytes, aioChannel, pipelineDirection);
        }
    }

    /**
     * Reads variable length 32bit int from buffer
     *
     * @return decoded int if buffers readerIndex has been forwarded else nonsense value
     */
    private static int readRawVarint32(AutoByteBuffer buffer) throws IOException {

        if (!buffer.hasRemaining()) {
            return 0;
        }
        //buffer.markReaderIndex();
        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if (!buffer.hasRemaining()) {
                buffer.reset();
                return 0;
            }
            if ((tmp = buffer.readByte()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if (!buffer.hasRemaining()) {
                    buffer.reset();
                    return 0;
                }
                if ((tmp = buffer.readByte()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if (!buffer.hasRemaining()) {
                        buffer.reset();
                        return 0;
                    }
                    if ((tmp = buffer.readByte()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        if (!buffer.hasRemaining()) {
                            buffer.reset();
                            return 0;
                        }
                        result |= (tmp = buffer.readByte()) << 28;
                        if (tmp < 0) {
                            throw new RuntimeException("malformed varint.");
                        }
                    }
                }
            }
            return result;
        }
    }
}
