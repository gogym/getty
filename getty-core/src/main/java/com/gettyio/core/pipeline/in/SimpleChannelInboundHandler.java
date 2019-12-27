/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.util.LinkedNonBlockQueue;

/**
 * 类名：SimpleChannelInboundHandler.java
 * 描述：简易的通道输出，继承这个类可容易实现消息接收
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class SimpleChannelInboundHandler<T> extends ChannelInboundHandlerAdapter {


    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {

        while (out.getCount() > 0) {
            channelRead(aioChannel, out.poll());
        }

    }


    @Override
    public void channelRead(AioChannel aioChannel, Object obj) throws Exception {
        channelRead0(aioChannel, (T) obj);
    }


    /**
     * 解码后的消息输出
     *
     * @param aioChannel 通道
     * @param t          解密后的消息
     * @throws Exception 异常
     */
    public abstract void channelRead0(AioChannel aioChannel, T t) throws Exception;

}
