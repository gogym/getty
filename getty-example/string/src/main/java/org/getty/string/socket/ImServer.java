package org.getty.string.socket;


import org.getty.core.channel.AioChannel;
import org.getty.core.channel.server.AioServerStarter;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.FixedLengthFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.ipfilter.IpFilterRuleHandler;
import org.getty.core.handler.ipfilter.IpFilterRuleType;
import org.getty.core.handler.ipfilter.IpRange;
import org.getty.core.handler.ssl.ClientAuth;
import org.getty.core.handler.ssl.SslConfig;
import org.getty.core.handler.ssl.SslHandler;
import org.getty.core.handler.ssl.SslService;
import org.getty.core.handler.timeout.HeartBeatTimeOutHandler;
import org.getty.core.handler.timeout.IdleStateHandler;
import org.getty.core.handler.traffic.ChannelTrafficShapingHandler;
import org.getty.core.pipeline.ChannelInitializer;
import org.getty.core.pipeline.DefaultChannelPipeline;
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

                String pkPath = ResourceUtils.getURL("classpath:serverStore.jks")
                        .getPath();
                SslConfig sSLConfig = new SslConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                sSLConfig.setClientMode(false);
                sSLConfig.setClientAuth(ClientAuth.NONE);
                SslService sSLService = new SslService(sSLConfig);
                //defaultChannelPipeline.addFirst(new SslHandler(channel.createSSL(sSLService)));

                //IpRange ir = new IpRange("127.0.0.1", "127.0.0.1");
                //List<IpRange> list = new ArrayList<>();
                //list.add(ir);
                // defaultChannelPipeline.addLast(new IpFilterRuleHandler(list, IpFilterRuleType.REJECT));

                //defaultChannelPipeline.addLast(new ChannelTrafficShapingHandler(1000));


                //defaultChannelPipeline.addLast(new IdleStateHandler(channel, 2, 0));
                //defaultChannelPipeline.addLast(new HeartBeatTimeOutHandler());

                //defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //defaultChannelPipeline.addLast(new FixedLengthFrameDecoder(4));
                defaultChannelPipeline.addLast(new StringDecoder());

                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();


        System.out.println("启动了服务器");

    }
}
