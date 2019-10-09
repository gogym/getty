package org.getty.protobuf.client;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.client.AioClientStarter;
import org.getty.core.handler.codec.protobuf.ProtobufEncoder;
import org.getty.core.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.codec.string.StringEncoder;
import org.getty.core.pipeline.ChannelInitializer;
import org.getty.core.pipeline.DefaultChannelPipeline;
import org.getty.core.util.ThreadPool;
import org.getty.protobuf.packet.MessageClass;

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
                    test(5555);
                }
            });
            i++;
        }
    }


    private static void test(int port) {

        AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                //责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();


                defaultChannelPipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                defaultChannelPipeline.addLast(new ProtobufEncoder());

                //定义消息解码器
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });

        try {
            client.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
            AioChannel aioChannel = client.getAioChannel();


            MessageClass.Message.Builder builder = MessageClass.Message.newBuilder();
            builder.setId("123");

            for (int i = 0; i < 10; i++) {
                aioChannel.writeAndFlush(builder.build().toByteArray());
                System.out.printf(""+i);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
