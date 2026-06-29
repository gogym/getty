package com.gettyio.protobuf.server;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufEncoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.protobuf.packet.MessageClass;

/**
 * Protobuf IM 服务端测试示例。
 * <p>
 * 默认监听 9999 端口，不启用 SSL。如需 SSL，取消下方注释。
 * 支持连接、心跳、单聊、群聊广播、ACK 确认等业务场景。
 * </p>
 */
public class ImServer {

    public static void main(String[] args) {
        AioServerStarter server = new AioServerStarter(9999);
        server.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.getChannelPipeline();

                // ----如需 SSL，取消以下注释----
                // String pkPath = getClass().getClassLoader().getResource("serverStore.jks").getPath();
                // SSLConfig sslConfig = new SSLConfig();
                // sslConfig.setKeyFile(pkPath);
                // sslConfig.setKeyPassword("123456");
                // sslConfig.setKeystorePassword("123456");
                // sslConfig.setTrustFile(pkPath);
                // sslConfig.setTrustPassword("123456");
                // sslConfig.setClientMode(false);
                // sslConfig.setClientAuthRequired(false);
                // pipeline.addFirst(new SSLHandler(sslConfig));
                // ----SSL END----

                // Protobuf 编码器
                pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                pipeline.addLast(new ProtobufEncoder());

                // Protobuf 解码器
                pipeline.addLast(new ProtobufVarint32FrameDecoder());
                pipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));

                // 业务处理器
                pipeline.addLast(new SimpleHandler());
            }
        });

        try {
            server.start();
            System.out.println("启动 Protobuf IM 服务, 端口: 9999");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
