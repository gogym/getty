package com.gettyio.string.udp;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.starter.NioServerStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketDecoder;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketEncoder;

/**
 * UDP 服务器示例
 * <p>
 * 基于 Java NIO 的 UDP 服务端演示。
 * 使用 DatagramPacketEncoder/Decoder 处理 UDP 数据报的编解码。
 * </p>
 * <p>
 * 启动后可运行 {@link UdpClient} 进行功能测试。
 * </p>
 */
public class UdpServer {

    /** 默认监听端口 */
    private static final int PORT = 8888;

    public static void main(String[] args) {
        try {
            // 创建 NIO 服务端启动器，指定 UDP 模式
            NioServerStarter server = new NioServerStarter(PORT);
            server.socketMode(SocketMode.UDP).channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(AbstractSocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.getChannelPipeline();

                    // UDP 数据报编码器：将 DatagramPacket 编码为字节写出
                    pipeline.addLast(new DatagramPacketEncoder());
                    // UDP 数据报解码器：将收到的字节解码为 DatagramPacket
                    pipeline.addLast(new DatagramPacketDecoder());

                    // 自定义业务处理器
                    pipeline.addLast(new SimpleHandler());
                }
            }).start();

            System.out.println("UDP 服务器已启动，端口: " + PORT);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
