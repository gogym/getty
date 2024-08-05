package com.gettyio.protobuf.client;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufEncoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.pipeline.ChannelInitializer;

import com.gettyio.core.util.thread.ThreadPool;
import com.gettyio.protobuf.packet.MessageClass;

import java.io.IOException;
import java.util.Scanner;
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
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                //责任链
                ChannelPipeline defaultChannelPipeline = channel.getChannelPipeline();
                //获取证书
                String pkPath = getClass().getClassLoader().getResource("clientStore.jks")
                        .getPath();
                //ssl配置
                SSLConfig sSLConfig = new SSLConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(true);
                //初始化ssl服务
                //SslService sSLService = new SslService(sSLConfig);
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
            public void onCompleted(final AbstractSocketChannel channel) {
                try {


                    final MessageClass.Message.Builder builder = MessageClass.Message.newBuilder();
                    builder.setBody("123");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("请输入需要发送的消息：");
                            Scanner sc = new Scanner(System.in);
                            while (sc.hasNext()) {
                                String s = sc.nextLine();
                                if (!s.equals("")) {

                                    builder.setBody(s);
                                    channel.writeAndFlush(builder.build());

                                }
                            }
                        }
                    }).start();

//                    int i = 0;
//                    for (; i < 1; i++) {
//                        channel.writeAndFlush(builder.build());
//                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                exc.printStackTrace();
            }
        });


    }

}
