package org.getty.string.client;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.client.AioClientStarter;
import org.getty.core.handler.codec.string.DelimiterFrameDecoder;
import org.getty.core.handler.codec.string.StringDecoder;
import org.getty.core.handler.codec.string.StringEncoder;
import org.getty.core.handler.ssl.ClientAuth;
import org.getty.core.handler.ssl.SslConfig;
import org.getty.core.handler.ssl.SslHandler;
import org.getty.core.handler.ssl.SslService;
import org.getty.core.pipeline.ChannelInitializer;
import org.getty.core.pipeline.DefaultChannelPipeline;
import org.getty.core.util.ThreadPool;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ImClient {


    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

        ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

        int i = 0;
        while (i < 1) {

            test(5555);
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


                SslConfig sSLConfig = new SslConfig();
                sSLConfig.setClientMode(true);
                SslService sSLService = new SslService(sSLConfig);
                //defaultChannelPipeline.addFirst(new SslHandler(channel.createSSL(sSLService)));


                //字符串编码器
                //defaultChannelPipeline.addLast(new StringEncoder());
                //指定结束符解码器
                // defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //字符串解码器
                // defaultChannelPipeline.addLast(new StringDecoder());
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
            Thread.sleep(2000);
            AioChannel aioChannel = client.getAioChannel();
            String s = "me12345\r\n";
            byte[] msgBody = s.getBytes("utf-8");
            long ct = System.currentTimeMillis();

            int i = 0;
            for (; i < 100000; i++) {
//                String s = i + "me\r\n";
//                byte[] msgBody = s.getBytes("utf-8");
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
