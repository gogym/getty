package org.getty.string.socket;


import org.getty.core.channel.AioChannel;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<String> {
    @Override
    public void channelAdded(AioChannel aioChannel) {
        System.out.println("连接过来了");
    }

    @Override
    public void channelClosed(AioChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AioChannel aioChannel, String str) {
        //System.out.println("读取消息:" + str);

//        try {
//            byte[]  msgBody = (str + "\r\n").getBytes("utf-8");
//            //返回消息给客户端
//            aioChannel.writeAndFlush(msgBody);
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        System.out.println("出错了");
    }

}
