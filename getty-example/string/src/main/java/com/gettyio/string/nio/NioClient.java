package com.gettyio.string.nio;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.AioClientConfig;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.NioClientStarter;
import com.gettyio.core.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.core.handler.codec.string.StringDecoder;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.handler.timeout.ReConnectHandler;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class NioClient {

    static ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 10);

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {


        int i = 0;
        while (i < 1) {

            test(8888);
            i++;
        }
    }


    private static void test(int port) {

        AioClientConfig aioConfig = new AioClientConfig();
        aioConfig.setHost("127.0.0.1");
        aioConfig.setPort(port);
        aioConfig.setClientChunkSize(512 * 1024 * 1024);
        aioConfig.setBufferWriterQueueSize(2 * 1024 * 1024);


        NioClientStarter client = new NioClientStarter(aioConfig);
        client.socketChannel(SocketMode.TCP).channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AioChannel channel) throws Exception {
                //责任链
                DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();


                //获取证书
                String pkPath =  getClass().getClassLoader().getResource("clientStore.jks")
                        .getPath();
                //ssl配置
                SslConfig sSLConfig = new SslConfig();
                sSLConfig.setKeyFile(pkPath);
                sSLConfig.setKeyPassword("123456");
                sSLConfig.setKeystorePassword("123456");
                sSLConfig.setTrustFile(pkPath);
                sSLConfig.setTrustPassword("123456");
                //设置服务器模式
                sSLConfig.setClientMode(true);
                //初始化ssl服务
                SslService sSLService = new SslService(sSLConfig);
               // defaultChannelPipeline.addFirst(new SslHandler(channel,sSLService));

                //defaultChannelPipeline.addLast(new ReConnectHandler(channel));

                //指定结束符解码器
                defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                //字符串解码器
                defaultChannelPipeline.addLast(new StringDecoder());
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
            Thread.sleep(3000);
            AioChannel aioChannel = client.getAioChannel();
            aioChannel.getChannelAttribute().put("key", "value");
            String s = "12\r\n";
            byte[] msgBody = s.getBytes("utf-8");
            long ct = System.currentTimeMillis();

            int i = 0;
            for (; i < 10; i++) {
//                String s = i + "me\r\n";
                // byte[] msgBody = s.getBytes("utf-8");
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
