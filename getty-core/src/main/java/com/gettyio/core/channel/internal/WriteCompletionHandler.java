/**
 * 包名：org.getty.core.channel.internal
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.internal;

import com.gettyio.core.channel.AioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.CompletionHandler;

/**
 * 类名：WriteCompletionHandler.java
 * 描述：写回调事件
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class WriteCompletionHandler implements CompletionHandler<Integer, AioChannel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteCompletionHandler.class);

    @Override
    public void completed(final Integer result, final AioChannel aioChannel) {
        try {
            aioChannel.writeCompleted();
        } catch (Exception e) {
            failed(e, aioChannel);
        }

    }

    @Override
    public void failed(Throwable exc, AioChannel aioChannel) {
        try {
            aioChannel.close();
        } catch (Exception e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }
}