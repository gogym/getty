package com.gettyio.protobuf.client;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.core.handler.codec.protobuf.ProtobufEncoder;
import com.gettyio.core.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.core.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.util.ThreadPool;
import com.gettyio.protobuf.packet.MessageClass;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ImClient {


    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

        ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

        int i = 0;
        while (i < 1) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    test(9999);
                }
            });
            i++;
        }
    }


    private static void test(int port) {

        AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                //责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();
                //获取证书
                String pkPath = getClass().getClassLoader().getResource("clientStore.jks")
                        .getPath();
                //ssl配置
                SslConfig sSLConfig = new SslConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(true);
                //初始化ssl服务
                SslService sSLService = new SslService(sSLConfig);
                //defaultChannelPipeline.addFirst(new SslHandler(channel, sSLService));

                defaultChannelPipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                defaultChannelPipeline.addLast(new ProtobufEncoder());

                //添加protobuf解码器
                defaultChannelPipeline.addLast(new ProtobufVarint32FrameDecoder());
                defaultChannelPipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));

                //定义消息解码器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });


        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(SocketChannel channel) {
                try {


                    MessageClass.Message.Builder builder = MessageClass.Message.newBuilder();
                    builder.setBody("12");
//                    while (true) {
//                        Thread.sleep(100);
//                        channel.writeAndFlush(builder.build());
//                    }

                    int i = 0;
                    for (; i < 1; i++) {
                        channel.writeAndFlush(builder.build());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {

            }
        });


    }

}
