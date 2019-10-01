/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com 
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline.in;


import org.getty.core.channel.AioChannel;
import org.getty.core.pipeline.ChannelboundHandler;

/**
 * 类名：ChannelInboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
public interface ChannelInboundHandler extends ChannelboundHandler {

    void channelAdded(AioChannel aioChannel);

    void channelClosed(AioChannel aioChannel);

    void channelRead(AioChannel aioChannel, Object obj);

    void decode(AioChannel aioChannel, byte[] bytes);

}
