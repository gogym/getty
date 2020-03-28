/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.pipeline.DatagramPacketHandler;
import com.gettyio.core.util.LinkedNonBlockQueue;

/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：简易的通道输出，继承这个类可容易实现消息接收
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class SimpleChannelInboundHandler<T> extends ChannelInboundHandlerAdapter implements DatagramPacketHandler {


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        while (out.getCount() > 0) {
            channelRead(socketChannel, out.poll());
        }

    }


    @Override
    public void channelRead(SocketChannel socketChannel, Object obj) throws Exception {
        channelRead0(socketChannel, (T) obj);
    }


    /**
     * 解码后的消息输出
     *
     * @param socketChannel 通道
     * @param t          解密后的消息
     * @throws Exception 异常
     */
    public abstract void channelRead0(SocketChannel socketChannel, T t) throws Exception;

}
