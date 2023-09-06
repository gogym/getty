package tcp;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

public class PbSimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(SocketChannel aioChannel,MessageClass.Message message) {

        try {

            String msgJson = JsonFormat.printer().print(message.toBuilder());
            System.out.println("读取消息了:" + msgJson);
            aioChannel.writeAndFlush(message);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        try {
//
//            MessageClass.Message message1= MessageClass.Message.parseFrom(message);
//             String msgJson = JsonFormat.printer().print(message1.toBuilder());
//            System.out.println("读取消息了:" + message1.getId());
//            System.out.println("读取消息了:" +msgJson);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("出错了");
    }
}
