/**
 * 包名：org.getty.core.channel.internal
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.internal;

import com.gettyio.core.channel.TcpChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.ThreadPool;

import java.nio.channels.CompletionHandler;

/**
 * 类名：ReadCompletionHandler.java
 * 描述：读回调事件
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class ReadCompletionHandler implements CompletionHandler<Integer, TcpChannel> {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ReadCompletionHandler.class);
    //线程池
    private ThreadPool executorService;

    public ReadCompletionHandler(ThreadPool executorService) {
        this.executorService = executorService;
    }

    @Override
    public void completed(final Integer result, final TcpChannel tcpChannel) {
        //通过多线程形式读取
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                tcpChannel.readFromChannel(result == -1);
            }
        });
    }

    @Override
    public void failed(Throwable exc, TcpChannel tcpChannel) {
        try {
            tcpChannel.close();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

    }
}