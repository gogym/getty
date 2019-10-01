package org.getty.string.socket;


import org.getty.core.channel.AioChannel;
import org.getty.core.channel.server.AioServerStarter;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.ipfilter.IpRange;
import org.getty.core.handler.ssl.ClientAuth;
import org.getty.core.handler.ssl.SslConfig;
import org.getty.core.handler.ssl.SslHandler;
import org.getty.core.handler.ssl.SslService;
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


//        AioQuickServer<String> server = new AioQuickServer<String>(8333, new StringProtocol(), new MessageProcessor<String>() {
//            public void process(AioSession<String> session, String msg) {
//                System.out.println("接受到客户端消息:" + msg);
//
//                byte[] response = "Hi Client!".getBytes();
//                byte[] head = {(byte) response.length};
//                try {
//                    session.writeBuffer().write(head);
//                    session.writeBuffer().write(response);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            public void stateEvent(AioSession<String> session, StateMachineEnum stateMachineEnum, Throwable throwable) {
//            }
//        });


        AioServerStarter server = new AioServerStarter(8333);
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

                IpRange ir = new IpRange("127.0.0.1", "127.0.0.1");
                List<IpRange> list = new ArrayList<>();
                list.add(ir);
                //defaultChannelPipeline.addLast(new IpFilterRuleHandler(list));

                //defaultChannelPipeline.addLast(new ChannelTrafficShapingHandler(10000));
                // defaultChannelPipeline.addLast(new IdleStateHandler(channel, 2, 0));
                //defaultChannelPipeline.addLast(new HeartBeatTimeOutHandler());

                //defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //defaultChannelPipeline.addLast(new FixedLengthFrameDecoder(128));
               // defaultChannelPipeline.addLast(new StringDecoder());
               // defaultChannelPipeline.addLast(new SimpleHandler());
            }
        });
        server.start();


        System.out.println("启动了服务器");

    }
}
