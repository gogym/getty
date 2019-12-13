/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.pipeline.ChannelboundHandler;

/**
 * 类名：ChannelInboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
public interface ChannelInboundHandler extends ChannelboundHandler {

    /**
     * 连接
     *
     * @return void
     * @params [aioChannel]
     */
    void channelAdded(AioChannel aioChannel);

    /**
     * 连接关闭
     *
     * @return void
     * @params [aioChannel]
     */
    void channelClosed(AioChannel aioChannel);

    /**
     * 消息读取
     *
     * @return void
     * @params [aioChannel, obj]
     */
    void channelRead(AioChannel aioChannel, Object obj);

    /**
     * 消息解码
     *
     * @return void
     * @params [aioChannel, bytes]
     */
    void decode(AioChannel aioChannel, byte[] bytes);

}
