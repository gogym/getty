/**
 * 包名：org.getty.core.handler.ipfilter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.ipfilter;

import org.getty.core.channel.AioChannel;
import org.getty.core.channel.ChannelState;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * 类名：AbstractRemoteAddressFilter.java
 * 描述：抽象IP过滤器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class AbstractRemoteAddressFilter<T extends SocketAddress> extends ChannelInboundHandlerAdapter {

    @Override
    public void handler(ChannelState channelStateEnum, byte[] bytes, Throwable cause, AioChannel aioChannel, PipelineDirection pipelineDirection) {
        super.handler(channelStateEnum, bytes, cause, aioChannel, pipelineDirection);
        switch (channelStateEnum) {
            case NEW_CHANNEL:
                handleNewChannel(aioChannel);
                break;
        }
    }

    /**
     * 执行IP验证
     *
     * @return void
     * @params [aioChannel]
     */
    private void handleNewChannel(AioChannel aioChannel) {
        try {
            T remoteAddress = (T) aioChannel.getRemoteAddress();
            if (remoteAddress == null) {
                aioChannel.close();
            }
            boolean flag = accept(aioChannel, remoteAddress);
            if (!flag) {
                aioChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Return true if connections from this IP address and port should be accepted. False otherwise.
     */
    protected abstract boolean accept(AioChannel aioChannel, T remoteAddress);


}
