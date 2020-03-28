/**
 * 包名：org.getty.core.handler.timeout
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.timeout;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.util.LinkedNonBlockQueue;


/**
 * 类名：HeartBeatTimeOutHandler.java
 * 描述：心跳检测
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class HeartBeatTimeOutHandler extends ChannelInboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HeartBeatTimeOutHandler.class);
    private int loss_connect_time = 0;

    @Override
    public void userEventTriggered(SocketChannel socketChannel, IdleState evt) throws Exception {
        if (evt == IdleState.READER_IDLE) {
            loss_connect_time++;
            if (loss_connect_time > 2) {
                // 超过3次检测没有心跳就关闭这个连接
                logger.info("[closed inactive channel:" + socketChannel.getRemoteAddress().getHostString() + "]");
                socketChannel.close();
            }
        }
        super.userEventTriggered(socketChannel, evt);
    }


    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        loss_connect_time = 0;
        super.decode(socketChannel, obj, out);
    }

}
