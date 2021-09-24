package com.gettyio.string.aio;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.core.handler.codec.string.StringDecoder;
import com.gettyio.core.handler.codec.string.StringEncoder;
import com.gettyio.core.handler.ssl.ClientAuth;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslHandler;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.expansion.handler.traffic.ChannelTrafficShapingHandler;

import java.net.StandardSocketOptions;

public class AioServer {


    public static void main(String[] args) {
        try {
            //初始化配置对象
            ServerConfig aioServerConfig = new ServerConfig();
            //设置host,不设置默认localhost
            aioServerConfig.setHost("127.0.0.1");
            //设置端口号
            aioServerConfig.setPort(8888);
            //设置服务器端内存池最大可分配空间大小，默认256mb，内存池空间可以根据吞吐量设置。
            // 尽量可以设置大一点，因为这不会真正的占用系统内存，只有真正使用时才会分配
            //设置数据输出器队列大小，一般不用设置这个参数，默认是10*1024*1024
            //aioServerConfig.setBufferWriterQueueSize(1024 * 1024);
            //设置读取缓存块大小，一般不用设置这个参数，默认128字节
            //aioServerConfig.setReadBufferSize(128);
            //设置SocketOptions
            //aioServerConfig.setOption(StandardSocketOptions.SO_RCVBUF, 8192);

            AioServerStarter server = new AioServerStarter(8888);
            server.channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(SocketChannel channel) throws Exception {
                    //获取责任链对象
                    DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                    //获取证书
                    String pkPath = getClass().getClassLoader().getResource("serverStore.jks").getPath();
                    //ssl配置
                    SslConfig sSLConfig = new SslConfig();
                    sSLConfig.setKeyFile(pkPath);
                    sSLConfig.setKeyPassword("123456");
                    sSLConfig.setKeystorePassword("123456");
                    //sSLConfig.setTrustFile(pkPath);
                    //sSLConfig.setTrustPassword("123456");
                    //设置服务器模式
                    sSLConfig.setClientMode(false);
                    //设置单向验证或双向验证
                    sSLConfig.setClientAuth(ClientAuth.NONE);
                    //初始化ssl服务
                    SslService sSLService = new SslService(sSLConfig);
                    //defaultChannelPipeline.addFirst(new SslHandler(channel, sSLService));

                    defaultChannelPipeline.addLast(new StringEncoder());
                    //添加 分隔符字符串处理器  按 "\r\n\" 进行消息分割
                    defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                    //添加字符串解码器
                    defaultChannelPipeline.addLast(new StringDecoder());

                    //添加自定义的简单消息处理器
                    defaultChannelPipeline.addLast(new SimpleHandler());
                }
            }).start();

            System.out.println("启动了TCP");


        } catch (Exception e) {

        }
    }

}
