package org.getty.protobuf.client;


import org.getty.core.channel.AioChannel;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.SimpleChannelInboundHandler;
import org.getty.protobuf.packet.MessageClass;

public class SimpleHandler extends SimpleChannelInboundHandler<MessageClass.Message> {
    @Override
    public void channelAdded(AioChannel aioChannel) {

        System.out.println("连接成功");

    }

    @Override
    public void channelClosed(AioChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AioChannel aioChannel, MessageClass.Message str) {
        System.out.println("读取消息了:" + str.getId());
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        System.out.println("出错了");
    }
}
