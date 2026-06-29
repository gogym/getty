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
package com.gettyio.expansion.handler.codec.websocket;

import com.gettyio.core.buffer.AutoByteBuffer;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.channel.ChannelState;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.websocket.frame.*;

/**
 * WebSocket 解码器。
 * <p>
 * 负责处理 WebSocket 连接的完整生命周期：
 * <ol>
 *   <li>握手阶段：解析客户端 HTTP 升级请求，发送握手响应</li>
 *   <li>数据阶段：解析 RFC 6455 帧格式，解码为具体 {@link WebSocketFrame} 子类</li>
 * </ol>
 * 支持 Hixie-76（版本 0~3）和 RFC 6455（版本 6+）协议。
 * </p>
 *
 * @author gogym
 */
public class WebSocketDecoder extends ByteToMessageDecoder {

    /** 协议版本，默认 0（握手完成后更新） */
    private int protocolVersion = WebSocketConstants.SPLIT_VERSION0;

    /** 是否已完成握手 */
    private boolean handShake = false;

    /** 当前正在解析的数据帧（用于半包场景） */
    private WebSocketFrame messageFrame;

    /** 接收数据缓冲区 */
    private final AutoByteBuffer byteBuffer = AutoByteBuffer.newByteBuffer();

    /** 握手请求信息 */
    private WebSocketRequest requestInfo = new WebSocketRequest();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object in) throws Exception {
        PooledByteBuffer buf = (PooledByteBuffer) in;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);

        if (handShake) {
            // 已握手：解析数据帧
            if (protocolVersion >= WebSocketConstants.SPLIT_VERSION6) {
                byteBuffer.writeBytes(bytes);
                WebSocketFrame frame = decodeFrame(byteBuffer);
                if (frame != null) {
                    ctx.fireChannelProcess(ChannelState.CHANNEL_READ, frame);
                    messageFrame = null;
                }
            } else {
                // 低版本协议透传给父类处理
                super.channelRead(ctx, bytes);
            }
        } else {
            // 未握手：解析握手请求
            byteBuffer.writeBytes(bytes);
            WebSocketHandShake.parserRequest(byteBuffer, requestInfo);
            if (requestInfo.getReadStatus() != WebSocketHandShake.READ_CONTENT) {
                // 数据不完整，等待更多数据
                return;
            }
            // 发送握手响应
            byte[] response = WebSocketHandShake.generateHandshake(requestInfo, ctx.channel()).getBytes();
            PooledByteBuffer responseBuf = ctx.channel().getByteBufferPool().acquire(response.length);
            responseBuf.writeBytes(response);
            if (ctx.channel().getSslHandler() == null) {
                ctx.channel().writeToSocket(responseBuf);
            } else {
                // SSL 模式下，握手信息需经 SSL 编码后直接发送，避免经过其他 encoder
                ctx.channel().getSslHandler().channelWrite(ctx, responseBuf);
            }
            // 唤醒写线程，确保握手响应立即发出
            ctx.channel().flush();
            protocolVersion = requestInfo.getSecVersion();
            handShake = true;
            ctx.channel().setChannelAttribute(WebSocketConstants.WEB_SOCKET_HAND_SHAKE, true);
            ctx.channel().setChannelAttribute(WebSocketConstants.WEB_SOCKET_PROTOCOL_VERSION, protocolVersion);
        }
        byteBuffer.clear();
    }


    /**
     * 解析 RFC 6455（版本 6+）数据帧。
     * <p>
     * 根据 opcode 构建对应的帧子类，并解析帧头部和负载数据。
     * 支持半包场景，多次调用直到帧完整。
     * </p>
     *
     * @param buffer 接收数据缓冲区
     * @return 解析完成的帧，未完成时返回 null
     */
    private WebSocketFrame decodeFrame(AutoByteBuffer buffer) throws Exception {
        do {
            if (messageFrame == null) {
                // 解析新帧：获取 opcode 并构建对应帧类型
                byte bt = buffer.read(0);
                byte opcode = (byte) (bt & 0x0F);
                Opcode op = Opcode.valueOf(opcode);
                if (op == null) {
                    return null;
                }
                switch (op) {
                    case CONTINUATION:
                        messageFrame = new ContinuationWebSocketFrame();
                        break;
                    case TEXT:
                        messageFrame = new TextWebSocketFrame();
                        break;
                    case BINARY:
                        messageFrame = new BinaryWebSocketFrame();
                        break;
                    case CLOSE:
                        messageFrame = new CloseWebSocketFrame();
                        break;
                    case PING:
                        messageFrame = new PingWebSocketFrame();
                        break;
                    case PONG:
                        messageFrame = new PongWebSocketFrame();
                        break;
                    default:
                        return null;
                }
            }

            // 解析帧数据（支持半包累积）
            messageFrame.parseMessage(buffer);
            if (messageFrame.isReadFinish()) {
                return messageFrame;
            }
        } while (buffer.hasRemaining());
        return null;
    }


    @Override
    public void channelClosed(ChannelHandlerContext ctx) throws Exception {
        handShake = false;
        protocolVersion = 0;
        messageFrame = null;
        byteBuffer.clear();
        requestInfo = new WebSocketRequest();
        super.channelClosed(ctx);
    }
}
