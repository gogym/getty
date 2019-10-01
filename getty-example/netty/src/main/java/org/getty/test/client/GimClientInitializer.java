package org.getty.test.client;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * netty处理器配置
 *
 * @author kokJuis
 * @version 1.0
 * @date 2016-9-30
 */
public class GimClientInitializer extends ChannelInitializer<Channel> {


    @Override
    public void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();


        // ----配置Protobuf处理器----
        // 用于decode前解决半包和粘包问题（利用包头中的包含数组长度来识别半包粘包）
        // pipeline.addLast(new ProtobufVarint32FrameDecoder());
        // //
        // 配置Protobuf解码处理器，消息接收到了就会自动解码，ProtobufDecoder是netty自带的，Message是自己定义的Protobuf类
        // pipeline.addLast(new ProtobufDecoder(Message.getDefaultInstance()));
        //
        // // 用于在序列化的字节数组前加上一个简单的包头，只包含序列化的字节长度。
        // pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        // // 配置Protobuf编码器，发送的消息会先经过编码
        // pipeline.addLast(new ProtobufEncoder());
        // ----Protobuf处理器END----


        // pipeline.addLast("framer", new DelimiterBasedFrameDecoder(1024,
        //       Delimiters.lineDelimiter()));
        // pipeline.addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
        //pipeline.addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
//        pipeline.addLast(new SimpleChannelInboundHandler<String>() {
//
//            @Override
//            public void handlerAdded(ChannelHandlerContext ctx)
//                    throws Exception {
//                Channel incoming = ctx.channel();
//                System.out.println("[Client] - " + incoming.remoteAddress()
//                        + " 连接过来");
//
//                //incoming.writeAndFlush("123\r\n456\r789\nabcde\r\n");
//            }
//
//            @Override
//            protected void channelRead0(ChannelHandlerContext ctx, String msg)
//                    throws Exception {
//
//                //System.out.println("收到消息：" + msg);
//
//            }
//
//        });


    }
}
