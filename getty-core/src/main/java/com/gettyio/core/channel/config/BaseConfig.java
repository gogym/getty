/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.channel.config;

import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;

/**
 * BaseConfig.java
 *
 * @description:配置项
 * @author:gogym
 * @date:2020/4/8
 * @copyright: Copyright by gettyio.com
 */
public class BaseConfig {

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

    /**
     * 版本
     */
    public static final String VERSION = "1.3.4";
    /**
     * 服务器地址
     */
    private String host;
    /**
     * 服务器端口号
     */
    private int port;
    /**
     * 消息读取缓存大小，默认2048
     */
    private int readBufferSize = 2048;
    /**
     * 内存池最大阻塞时间。默认1s
     */
    private int chunkPoolBlockTime = 1000;
    /**
     * 输出类队列大小，默认10*1024*1024
     */
    private int bufferWriterQueueSize = 10 * 1024 * 1024;
    /**
     * 流控阈值
     */
    private final int flowControlSize = 20;
    /**
     * 释放流控阈值
     */
    private final int releaseFlowControlSize = 10;

    /**
     * 设置Socket的TCP参数配置
     * AIO客户端的可选为：
     * 套接字发送缓冲区的大小。int
     * 1. StandardSocketOptions.SO_SNDBUF
     * 套接字接收缓冲区的大小。int
     * 2. StandardSocketOptions.SO_RCVBUF
     * 使连接保持活动状态。boolean
     * 3. StandardSocketOptions.SO_KEEPALIVE
     * 重用地址。boolean
     * 4. StandardSocketOptions.SO_REUSEADDR
     * 禁用Nagle算法。boolean
     * 5. StandardSocketOptions.TCP_NODELAY
     * AIO客户端的有效可选范围为：
     * 2. StandardSocketOptions.SO_RCVBUF
     * 4. StandardSocketOptions.SO_REUSEADDR
     */
    private Map<SocketOption<Object>, Object> socketOptions;

    /**
     * 是否开启零拷贝
     */
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
            socketOptions = new HashMap<>(64);
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
        return "Config{readBufferSize=" + readBufferSize +
                ", host='" + host == null ? "localhost" : host + '\'' +
                ", port=" + port +
                ", socketOptions=" + socketOptions +
                '}';
    }

}
