package test.http;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.expansion.handler.codec.http.request.HttpRequestDecoder;
import com.gettyio.expansion.handler.codec.http.response.HttpResponseEncoder;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.DefaultChannelPipeline;

public class TcpServer {


    public static void main(String[] args) {
        try {

            //初始化配置对象
            ServerConfig aioServerConfig = new ServerConfig();
            //设置host,不设置默认localhost
            aioServerConfig.setHost("127.0.0.1");
            //设置端口号
            aioServerConfig.setPort(8888);
            AioServerStarter server = new AioServerStarter(8888);
            server.channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(SocketChannel channel) throws Exception {
                    //获取责任链对象
                    DefaultChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();

                    //添加http response 编码器
                    defaultChannelPipeline.addLast(new HttpResponseEncoder());
                    //添加http request 解码器
                    defaultChannelPipeline.addLast(new HttpRequestDecoder());
                    //添加自定义的简单消息处理器
                    defaultChannelPipeline.addLast(new SimpleHandler());
                }
            }).start();


            System.out.println("启动了http,可以请求了：http://localhost:8888");
        } catch (Exception e) {

        }
    }

}
