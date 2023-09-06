package com.gettyio.string.nio;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.starter.NioServerStarter;
import com.gettyio.core.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.core.handler.codec.string.StringDecoder;
import com.gettyio.core.handler.codec.string.StringEncoder;
import com.gettyio.core.handler.ssl.ClientAuth;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;

public class NioServer {


    public static void main(String[] args) {
        NioServer ns = new NioServer();
        ns.test(8888);
        // ns.test(8889);
    }


    public void test(int port) {
        try {
            //初始化配置对象
            ServerConfig aioServerConfig = new ServerConfig();
            //设置host,不设置默认localhost
            aioServerConfig.setHost("127.0.0.1");
            //设置端口号
            aioServerConfig.setPort(port);
            //设置数据输出器队列大小，一般不用设置这个参数，默认是10*1024*1024
            //aioServerConfig.setBufferWriterQueueSize(10*1024*1024);
            //设置读取缓存块大小，一般不用设置这个参数，默认128字节


            NioServerStarter server = new NioServerStarter(port);
            server.socketMode(SocketMode.TCP).channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(SocketChannel channel) throws Exception {
                    //获取责任链对象
                    ChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                    //获取证书
                    String pkPath = getClass().getClassLoader().getResource("serverStore.jks").getPath();
                    //ssl配置
                    SSLConfig sSLConfig = new SSLConfig();
                    sSLConfig.setKeyFile(pkPath);
                    sSLConfig.setKeyPassword("123456");
                    sSLConfig.setKeystorePassword("123456");
                    sSLConfig.setTrustFile(pkPath);
                    sSLConfig.setTrustPassword("123456");
                    //设置服务器模式
                    sSLConfig.setClientMode(false);
                    //设置单向验证或双向验证
                    sSLConfig.setClientAuth(ClientAuth.REQUIRE);
                    //初始化ssl服务
                    //defaultChannelPipeline.addFirst(new SSLHandler(sSLConfig));

                    defaultChannelPipeline.addLast(new StringEncoder());
                    //添加 分隔符字符串处理器  按 "\r\n\" 进行消息分割
                    defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                    //添加字符串解码器
                    defaultChannelPipeline.addLast(new StringDecoder());
                    //添加自定义的简单消息处理器
                    defaultChannelPipeline.addLast(new SimpleHandler());
                }
            }).start();


            System.out.println("启动了NIO TCP");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
