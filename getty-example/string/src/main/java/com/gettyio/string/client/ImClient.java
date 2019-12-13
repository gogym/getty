package com.gettyio.string.client;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.AioConfig;
import com.gettyio.core.channel.client.AioClientConfig;
import com.gettyio.core.channel.client.AioClientStarter;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ImClient {

    static ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {


        int i = 0;
        while (i < 1) {

            test(5555);
            i++;
        }
    }


    private static void test(int port) {

        AioClientConfig aioConfig = new AioClientConfig();
        aioConfig.setHost("127.0.0.1");
        aioConfig.setPort(port);
        aioConfig.setClientChunkSize(50 * 1024 * 1024);

        AioClientStarter client = new AioClientStarter(aioConfig);
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
            for (; i < 1000000; i++) {
//                String s = i + "me\r\n";
//                byte[] msgBody = s.getBytes("utf-8");
                aioChannel.writeAndFlush(msgBody);
                //aioChannel.writeAndFlush(msgBody);
                //aioChannel.writeAndFlush(msgBody);
                //aioChannel.writeAndFlush(msgBody);
            }

            long lt = System.currentTimeMillis();
            System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");
            System.out.printf("发送消息数量：" + i + "条\r\n");


//            for (int j = 0; j < 1; j++) {
//               threadPool.execute(new Runnable() {
//                   @Override
//                   public void run() {
//                       int i = 0;
//                       for (; i < 10; i++) {
////                String s = i + "me\r\n";
////                byte[] msgBody = s.getBytes("utf-8");
//                           aioChannel.writeAndFlush(msgBody);
//                           //aioChannel.writeAndFlush(msgBody);
//                           //aioChannel.writeAndFlush(msgBody);
//                           //aioChannel.writeAndFlush(msgBody);
//                       }
//
//                       long lt = System.currentTimeMillis();
//                       System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");
//                       System.out.printf("发送消息数量：" + i + "条\r\n");
//                   }
//               });
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
