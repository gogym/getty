/**
 * 包名：org.getty.core.handler.ssl
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.ssl;

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.ssl.sslfacade.IHandshakeCompletedListener;
import com.gettyio.core.handler.ssl.sslfacade.ISSLListener;
import com.gettyio.core.handler.ssl.sslfacade.ISessionClosedListener;
import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.util.LinkedNonBlockQueue;

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
    private SocketChannel socketChannel;
    LinkedNonBlockQueue<Object> out;

    public SslHandler(SocketChannel socketChannel, SslService sslService) {
        this.socketChannel = socketChannel;
        this.sslService = sslService;
        this.socketChannel.setSslHandler(this);
        sslService.createSSLFacade(new handshakeCompletedListener(), new SSLListener(), new sessionClosedListener());
    }


    public SslService getSslService() {
        return sslService;
    }

    @Override
    public void encode(SocketChannel socketChannel, Object obj) throws Exception {
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
                socketChannel.writeToChannel(b);
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
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
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
                socketChannel.writeToChannel(b);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                sslService.getSsl().close();
                return;
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
            socketChannel.close();
            return;
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
                //aioChannel.writeToChannel(b);
                SslHandler.super.encode(socketChannel, b);
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
                SslHandler.super.decode(socketChannel, b, out);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


}
