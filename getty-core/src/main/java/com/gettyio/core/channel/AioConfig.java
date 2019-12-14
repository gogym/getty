/**
 * 包名：org.getty.core.channel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel;

import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 类名：AioConfig.java
 * 描述：配置项
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class AioConfig {

    public static final String BANNER =
            "                       tt     yt             \n" +
                    "                       tt     ye             \n" +
                    "  ttttt      tttt     teet   ytety   tt   ty \n" +
                    " tetytgt    yey tt     et     tey    tey yet \n" +
                    "ytt  yet    et   ey    tt     ye     yet tey \n" +
                    "yet  yet    getttty    tt     ye      ttyet  \n" +
                    "ytt  ygt    et         tt     ye      yetey  \n" +
                    " tetytgt    yetytt     teyy   yeyy     tgt   \n" +
                    "     tet     tttty     ytty    tty     tey   \n" +
                    "ytt  yey                               te    \n" +
                    " ttttty                              yttt    \n" +
                    "   yy                                yyy     \n";

    //版本
    public static final String VERSION = "v1.0.0.RELEASE";

    //流控阈值
    private final int flowControlSize = 20;
    //释放流控阈值
    private final int releaseFlowControlSize = 10;

    //消息读取缓存大小，默认512
    private int readBufferSize = 512;
    //内存池最大阻塞时间。默认1s
    private int chunkPoolBlockTime = 1000;
    //输出类队列大小，默认1024*1024
    private int bufferWriterQueueSize = 1024 * 1024;

    //服务器IP
    private String host;
    //服务器端口号
    private int port;
    //Socket 配置
    private Map<SocketOption<Object>, Object> socketOptions;
    //是否开启零拷贝
    private final Boolean isDirect = true;

    //------------------------------------------------------------------------------------------------

    public final String getHost() {
        return host;
    }

    public final void setHost(String host) {
        this.host = host;
    }

    public final int getPort() {
        return port;
    }

    public final void setPort(int port) {
        this.port = port;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getBufferWriterQueueSize() {
        return bufferWriterQueueSize;
    }

    public void setBufferWriterQueueSize(int bufferWriterQueueSize) {
        this.bufferWriterQueueSize = bufferWriterQueueSize;
    }

    public Map<SocketOption<Object>, Object> getSocketOptions() {
        return socketOptions;
    }

    public void setOption(SocketOption socketOption, Object f) {
        if (socketOptions == null) {
            socketOptions = new HashMap<>();
        }
        socketOptions.put(socketOption, f);
    }

    public Boolean isDirect() {
        return isDirect;
    }

    public int getChunkPoolBlockTime() {
        return chunkPoolBlockTime;
    }

    public void setChunkPoolBlockTime(int chunkPoolBlockTime) {
        this.chunkPoolBlockTime = chunkPoolBlockTime;
    }

    @Override
    public String toString() {
        return "AioConfig{readBufferSize=" + readBufferSize +
                ", host='" + host == null ? "localhost" : host + '\'' +
                ", port=" + port +
                ", socketOptions=" + socketOptions +
                '}';
    }

}
