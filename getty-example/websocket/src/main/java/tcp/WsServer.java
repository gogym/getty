package tcp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.websocket.WebSocketDecoder;
import com.gettyio.expansion.handler.codec.websocket.WebSocketEncoder;

/**
 * WebSocket 服务端测试示例。
 * <p>
 * 默认监听 8888 端口，不启用 SSL。如需 SSL，取消下方注释。
 * </p>
 */
public class WsServer {

    public static void main(String[] args) {
        try {
            GettyConfig config = new GettyConfig();
            config.setPort(8888);

            AioServerStarter server = new AioServerStarter(config);
            server.channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(AbstractSocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.getChannelPipeline();

                    // ----如需 SSL，取消以下注释----
                    // String pkPath = getClass().getClassLoader().getResource("serverStore.jks").getPath();
                    // SSLConfig sslConfig = new SSLConfig();
                    // sslConfig.setKeyFile(pkPath);
                    // sslConfig.setKeyPassword("123456");
                    // sslConfig.setKeystorePassword("123456");
                    // sslConfig.setTrustFile(pkPath);
                    // sslConfig.setTrustPassword("123456");
                    // sslConfig.setClientMode(false);
                    // sslConfig.setClientAuthRequired(false);
                    // pipeline.addFirst(new SSLHandler(sslConfig));
                    // ----SSL END----

                    pipeline.addLast(new WebSocketEncoder());
                    pipeline.addLast(new WebSocketDecoder());
                    pipeline.addLast(new SimpleHandler());
                }
            }).start();
            System.out.println("启动 WebSocket 服务, 端口: " + config.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
