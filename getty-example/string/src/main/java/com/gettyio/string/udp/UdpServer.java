package com.gettyio.string.udp;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.SocketMode;
import com.gettyio.core.channel.config.ServerConfig;
import com.gettyio.core.channel.starter.NioServerStarter;
import com.gettyio.core.handler.codec.string.DelimiterFrameDecoder;
import com.gettyio.core.handler.codec.string.StringDecoder;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketDecoder;
import com.gettyio.expansion.handler.codec.datagramPacket.DatagramPacketEncoder;

public class UdpServer {


    public static void main(String[] args) {
        UdpServer udpServer = new UdpServer();
        udpServer.startUdp();
    }


    private void startUdp() {
        try {
            //初始化配置对象
            ServerConfig aioServerConfig = new ServerConfig();
            //设置host,不设置默认localhost
            aioServerConfig.setHost("127.0.0.1");
            //设置端口号
            aioServerConfig.setPort(8888);

            NioServerStarter server = new NioServerStarter(8888);
            server.socketMode(SocketMode.UDP).channelInitializer(new ChannelInitializer() {
                @Override
                public void initChannel(SocketChannel channel) throws Exception {
                    //获取责任链对象
                    ChannelPipeline defaultChannelPipeline = channel.getDefaultChannelPipeline();


                    defaultChannelPipeline.addLast(new DatagramPacketEncoder());
                    defaultChannelPipeline.addLast(new DatagramPacketDecoder());

                    //添加 分隔符字符串处理器  按 "\r\n\" 进行消息分割
                    //defaultChannelPipeline.addLast(new DelimiterFrameDecoder(DelimiterFrameDecoder.lineDelimiter));
                    //添加字符串解码器
                    //defaultChannelPipeline.addLast(new StringDecoder());

                    defaultChannelPipeline.addLast(new SimpleHandler());
                }
            }).start();
            System.out.println("启动Udp");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
