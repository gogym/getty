package tcp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.websocket.frame.*;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

/**
 * WebSocket 服务端处理器 —— 回显模式：收到什么帧就原样返回。
 * 支持文本、二进制、Ping、Pong、Close、Continuation 全部帧类型。
 */
public class SimpleHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端连接成功");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[服务端] 客户端连接关闭");
    }

    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, WebSocketFrame frame) {

        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            System.out.println("[服务端] 收到文本帧, 长度=" + text.length());
            // 原样回显
            aioChannel.writeAndFlush(new TextWebSocketFrame(text));

        } else if (frame instanceof BinaryWebSocketFrame) {
            byte[] data = frame.getPayloadData();
            System.out.println("[服务端] 收到二进制帧, 长度=" + data.length);
            // 原样回显
            aioChannel.writeAndFlush(new BinaryWebSocketFrame(data));

        } else if (frame instanceof PingWebSocketFrame) {
            System.out.println("[服务端] 收到 Ping 帧");
            // 回复 Pong
            aioChannel.writeAndFlush(new PongWebSocketFrame());

        } else if (frame instanceof PongWebSocketFrame) {
            System.out.println("[服务端] 收到 Pong 帧");

        } else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("[服务端] 收到 Close 帧");
            // 回复 Close
            aioChannel.writeAndFlush(new CloseWebSocketFrame());

        } else if (frame instanceof ContinuationWebSocketFrame) {
            byte[] data = frame.getPayloadData();
            System.out.println("[服务端] 收到 Continuation 帧, 长度=" + data.length);
            // 原样回显
            ContinuationWebSocketFrame resp = new ContinuationWebSocketFrame();
            resp.setPayloadData(data);
            aioChannel.writeAndFlush(resp);

        } else {
            System.out.println("[服务端] 收到未知帧类型, opcode=" + frame.getOpcode());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[服务端] 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
