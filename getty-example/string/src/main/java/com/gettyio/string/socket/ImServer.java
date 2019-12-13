package com.gettyio.string.socket;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.server.AioServerStarter;
import com.gettyio.core.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.core.handler.codec.string.StringDecoder;
import com.gettyio.core.handler.ssl.ClientAuth;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

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

                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //defaultChannelPipeline.addLast(new FixedLengthFrameDecoder(4));
                defaultChannelPipeline.addLast(new StringDecoder());

                defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();


        System.out.println("启动了服务器");

    }
}
