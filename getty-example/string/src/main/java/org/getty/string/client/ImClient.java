package org.getty.string.client;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.client.AioClientStarter;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.ssl.SslConfig;
import org.getty.core.handler.ssl.SslHandler;
import org.getty.core.handler.ssl.SslService;
import org.getty.core.pipeline.ChannelInitializer;
import org.getty.core.pipeline.DefaultChannelPipeline;
import org.getty.core.util.ThreadPool;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutionException;

public class ImClient {


    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

        ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

        int i = 0;
        while (i < 1) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    test(8333);
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
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                String pkPath = ResourceUtils.getURL("classpath:clientStore.jks")
                        .getPath();
                SslConfig sSLConfig = new SslConfig();
                //sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setClientMode(true);
                SslService sSLService = new SslService(sSLConfig);
                //defaultChannelPipeline.addFirst(new SslHandler(channel.createSSL(sSLService)));

                //defaultChannelPipeline.addLast(new StringEncoder());
                //defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                // defaultChannelPipeline.addLast(new StringDecoder());
                //defaultChannelPipeline.addLast(new SimpleHandler());
            }
        })


        ;
        try {
            client.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
            //byte[] msgBody = s.getBytes("utf-8");
            AioChannel aioChannel = client.getAioChannel();
            String s = "me\r\n";
            byte[] msgBody = s.getBytes("utf-8");
            long ct = System.currentTimeMillis();

            int i = 0;
            for (; i < 1000000; i++) {
                aioChannel.writeAndFlush(msgBody);
            }

            long lt = System.currentTimeMillis();
            System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");
            System.out.printf("发送消息数量：" + i + "条\r\n");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
