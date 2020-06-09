package com.gettyio.mqtt.server;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.handler.codec.mqtt.*;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.mqtt.client.SimpleHandler;


public class MqttServer {
    public static void main(String[] args) {

        AioServerStarter server = new AioServerStarter(9999);
        server.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {

                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                defaultChannelPipeline.addLast(MqttEncoder.INSTANCE);
                defaultChannelPipeline.addLast(new MqttDecoder());

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
