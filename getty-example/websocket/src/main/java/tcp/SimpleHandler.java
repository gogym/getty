package tcp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.websocket.frame.*;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, WebSocketFrame frame) {

        if (frame instanceof TextWebSocketFrame) {
            System.out.println("类型匹配:" + ((TextWebSocketFrame) frame).text());
            //发送一个数据帧
            TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(((TextWebSocketFrame) frame).text());
            aioChannel.writeAndFlush(textWebSocketFrame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            System.out.println("类型匹配:" + ((BinaryWebSocketFrame) frame).getPayloadLen());
            System.out.println(new String(frame.getPayloadData()));
            BinaryWebSocketFrame binaryWebSocketFrame=new BinaryWebSocketFrame("qqq".getBytes());
            aioChannel.writeAndFlush(binaryWebSocketFrame);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("出错了");
    }
}
