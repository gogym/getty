package com.gettyio.string.aio;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.expansion.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.expansion.handler.codec.string.StringDecoder;
import com.gettyio.expansion.handler.codec.string.StringEncoder;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.timeout.IdleStateHandler;

/**
 * AIO TCP 服务器示例
 * <p>
 * 基于 Java AIO（异步IO）的 TCP 服务端演示。
 * 使用 DelimiterFrameDecoder 按 "\r\n" 进行消息分帧，
 * StringDecoder/StringEncoder 负责字符串编解码。
 * </p>
 * <p>
 * 启动后可运行 {@link AioClient} 进行功能测试。
 * </p>
 */
public class AioServer {

    /** 默认监听端口 */
    private static final int PORT = 8888;

    public static void main(String[] args) {
        try {
            // 创建 AIO 服务端启动器
            AioServerStarter server = new AioServerStarter(PORT);
            server.channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(AbstractSocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.getChannelPipeline();

                    // 空闲检测：读空闲 60 秒超时（可选，演示心跳机制）
                    pipeline.addLast(new IdleStateHandler(60, 0));

                    // 字符串编码器：将 String 编码为字节写出
                    pipeline.addLast(new StringEncoder());
                    // 分隔符分帧器：按 "\r\n" 切分粘包/半包
                    pipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.LINE_DELIMITER));
                    // 字符串解码器：将字节解码为 String
                    pipeline.addLast(new StringDecoder());

                    // 自定义业务处理器
                    pipeline.addLast(new SimpleHandler());
                }
            }).start();

            System.out.println("AIO TCP 服务器已启动，端口: " + PORT);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
