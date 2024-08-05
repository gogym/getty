package com.gettyio.mqtt.server;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.mqtt.MqttDecoder;
import com.gettyio.expansion.handler.codec.mqtt.MqttEncoder;
import com.gettyio.mqtt.client.SimpleHandler;


/**
 * mqtt服务器
 */
public class MqttServer {
    public static void main(String[] args) {

        AioServerStarter server = new AioServerStarter(9999);
        server.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {

                ChannelPipeline defaultChannelPipeline = channel.getChannelPipeline();
                //添加mqtt编解码器
                defaultChannelPipeline.addLast(MqttEncoder.INSTANCE);
                defaultChannelPipeline.addLast(new MqttDecoder());
                //添加处理器
                defaultChannelPipeline.addLast(new SimpleHandler());

            }
        });
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("启动了mqtt服务器");

    }
}
