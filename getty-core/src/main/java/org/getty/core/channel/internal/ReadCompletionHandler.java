/**
 * 包名：org.getty.core.channel.internal
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.internal;

import org.getty.core.channel.AioChannel;
import org.getty.core.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;

/**
 * 类名：ReadCompletionHandler.java
 * 描述：读回调事件
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class ReadCompletionHandler implements CompletionHandler<Integer, AioChannel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadCompletionHandler.class);
    //线程池
    private ThreadPool executorService;

    public ReadCompletionHandler(ThreadPool executorService) {
        this.executorService = executorService;
    }

    @Override
    public void completed(final Integer result, final AioChannel aioChannel) {
        //通过多线程形式读取
        executorService.execute(() -> aioChannel.readFromChannel(result == -1));
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