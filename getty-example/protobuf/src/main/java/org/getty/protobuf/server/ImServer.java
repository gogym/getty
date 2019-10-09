package org.getty.protobuf.server;


import org.getty.core.channel.AioChannel;
import org.getty.core.channel.server.AioServerStarter;
import org.getty.core.handler.codec.protobuf.ProtobufDecoder;
import org.getty.core.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.ipfilter.IpRange;
import org.getty.core.handler.ssl.ClientAuth;
import org.getty.core.handler.ssl.SslConfig;
import org.getty.core.handler.ssl.SslService;
import org.getty.core.pipeline.ChannelInitializer;
import org.getty.core.pipeline.DefaultChannelPipeline;
import org.getty.protobuf.packet.MessageClass;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

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
