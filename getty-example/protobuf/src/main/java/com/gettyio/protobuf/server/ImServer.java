package com.gettyio.protobuf.server;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.server.AioServerStarter;
import com.gettyio.core.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.core.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.protobuf.packet.MessageClass;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ImServer implements CommandLineRunner {


    @Override
    public void run(String... args) throws Exception {


        AioServerStarter server = new AioServerStarter(5555);
        server.bossThreadNum(10).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();


                defaultChannelPipeline.addLast(new ProtobufVarint32FrameDecoder());
                defaultChannelPipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));
                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();


        System.out.println("启动了服务器");

    }
}
