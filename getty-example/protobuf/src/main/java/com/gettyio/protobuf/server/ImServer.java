package com.gettyio.protobuf.server;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufEncoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import com.gettyio.core.handler.ssl.ClientAuth;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.expansion.handler.traffic.ChannelTrafficShapingHandler;
import com.gettyio.protobuf.packet.MessageClass;

public class ImServer {


    public static void main(String[] args) {

        AioServerStarter server = new AioServerStarter(9999);
        server.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {

                ChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                //获取证书
                String pkPath = getClass().getClassLoader().getResource("serverStore.jks").getPath();
                //ssl配置
                SSLConfig sSLConfig = new SSLConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(false);
                //设置单向验证或双向验证
                sSLConfig.setClientAuth(ClientAuth.REQUIRE);
                //初始化ssl服务
                //SslService sSLService = new SslService(sSLConfig);
                //defaultChannelPipeline.addFirst(new SslHandler(channel, sSLService));

                //ChannelTrafficShapingHandler channelTrafficShapingHandler = new ChannelTrafficShapingHandler(5000);
                //defaultChannelPipeline.addLast(channelTrafficShapingHandler);
                //System.out.println(channelTrafficShapingHandler.getTotalRead());


                //添加protobuf编码器
                defaultChannelPipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                defaultChannelPipeline.addLast(new ProtobufEncoder());

                //添加protobuf解码器
                defaultChannelPipeline.addLast(new ProtobufVarint32FrameDecoder());
                defaultChannelPipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));

                defaultChannelPipeline.addLast(new SimpleHandler());

            }
        });
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }


        System.out.println("启动了服务器");

    }
}
