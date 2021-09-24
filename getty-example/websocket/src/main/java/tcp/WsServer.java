package tcp;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.handler.ssl.ClientAuth;
import com.gettyio.core.handler.ssl.SslConfig;
import com.gettyio.core.handler.ssl.SslHandler;
import com.gettyio.core.handler.ssl.SslService;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;
import com.gettyio.expansion.handler.codec.websocket.WebSocketDecoder;
import com.gettyio.expansion.handler.codec.websocket.WebSocketEncoder;

import java.net.StandardSocketOptions;

public class WsServer {


    public static void main(String[] args) {
        try {
            //初始化配置对象
            ServerConfig aioServerConfig = new ServerConfig();
            //设置host,不设置默认localhost
            //aioServerConfig.setHost("127.0.0.1");
            //设置端口号
            aioServerConfig.setPort(8888);

            AioServerStarter server = new AioServerStarter(aioServerConfig);
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
                    sSLConfig.setTrustFile(pkPath);
                    sSLConfig.setTrustPassword("123456");
                    //设置服务器模式
                    sSLConfig.setClientMode(false);
                    //设置单向验证或双向验证
                    sSLConfig.setClientAuth(ClientAuth.NONE);
                    //初始化ssl服务
                    SslService sslService = new SslService(sSLConfig);
                    defaultChannelPipeline.addFirst(new SslHandler(channel, sslService));

                    defaultChannelPipeline.addLast(new WebSocketEncoder());
                    defaultChannelPipeline.addLast(new WebSocketDecoder());

                    defaultChannelPipeline.addLast(new SimpleHandler());

                    // ----配置Protobuf处理器----
//                    defaultChannelPipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));
//                    defaultChannelPipeline.addLast(new ProtobufEncoder());
//                    defaultChannelPipeline.addLast(new PbSimpleHandler());
                    // ----Protobuf处理器END----

                }
            }).start();
            System.out.println("启动ws服务");
        } catch (Exception e) {

        }
    }

}
