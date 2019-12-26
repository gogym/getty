/**
 * 包名：org.getty.core.handler.ssl
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.ssl;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.handler.ssl.sslfacade.ISSLListener;
import com.gettyio.core.handler.ssl.sslfacade.ISessionClosedListener;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.ArrayList;
import com.gettyio.core.util.LinkedBlockQueue;

import java.nio.ByteBuffer;

/**
 * 类名：SslHandler.java
 * 描述：SSL 编解码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class SslHandler extends ChannelAllBoundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslHandler.class);

    private SslService sslService;
    private AioChannel aioChannel;
    LinkedBlockQueue<Object> out;

    public SslHandler(AioChannel aioChannel) {
        this.aioChannel = aioChannel;
        sslService = aioChannel.getSSLService();
        aioChannel.getSSLService().createSSLFacade(new handshakeCompletedListener(), new SSLListener(), new sessionClosedListener());
    }


    @Override
    public void encode(AioChannel aioChannel, Object obj) throws Exception {
        byte[] bytes = (byte[]) obj;
        if (!sslService.getSsl().isHandshakeCompleted() && obj != null) {
            //握手
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            try {
                //byteBuffer.compact();
                //byteBuffer.flip();
                sslService.getSsl().decrypt(byteBuffer);

                byte[] b = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes, 0, b.length);
                aioChannel.writeToChannel(b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                sslService.getSsl().close();
            }
        } else if (bytes != null) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //SSL doUnWard
            //byteBuffer.compact();
            // byteBuffer.flip();
            sslService.getSsl().encrypt(byteBuffer);
        }
    }

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedBlockQueue<Object> out) throws Exception {
        this.out = out;
        byte[] bytes = (byte[]) obj;
        if (!sslService.getSsl().isHandshakeCompleted() && obj != null) {
            //握手
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            try {
                //byteBuffer.compact();
                //byteBuffer.flip();
                sslService.getSsl().decrypt(byteBuffer);

                byte[] b = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes, 0, b.length);
                aioChannel.writeToChannel(b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                sslService.getSsl().close();
            }
        } else if (bytes != null) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //SSL doUnWard
            //byteBuffer.compact();
            // byteBuffer.flip();
            sslService.getSsl().decrypt(byteBuffer);
        }
    }


    /**
     * 握手成功回调
     */
    class handshakeCompletedListener implements IHandshakeCompletedListener {
        @Override
        public void onComplete() {
            logger.info("Handshake Completed");
        }
    }


    /**
     * 握手关闭回调
     */
    class sessionClosedListener implements ISessionClosedListener {

        @Override
        public void onSessionClosed() {
            logger.info("Handshake failure");
            //当握手失败时，关闭当前客户端连接
            aioChannel.close();
        }
    }


    /**
     * 消息回调
     */
    class SSLListener implements ISSLListener {

        @Override
        public void onWrappedData(ByteBuffer wrappedBytes) {
            try {
//                wrappedBytes.compact();
//                wrappedBytes.flip();
                byte[] b = new byte[wrappedBytes.remaining()];
                wrappedBytes.get(b, 0, b.length);
                //回调父类方法
                SslHandler.super.encode(aioChannel, b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        @Override
        public void onPlainData(ByteBuffer plainBytes) {
            //消息解码
            byte[] b = new byte[plainBytes.remaining()];
            plainBytes.get(b, 0, b.length);
            try {
                SslHandler.super.decode(aioChannel, b, out);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
