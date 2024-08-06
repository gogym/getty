/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
 */
public abstract class BaseConfig {

    /**
     * 服务器地址
     */
    private String host;
    /**
     * 服务器端口号
     */
    private int port;
    /**
     * 消息读取缓存大小，默认32
     */
    private int readBufferSize = 32;
    /**
     * 输出类队列大小,再大意义不大，因为实际写出速度还会受到机器配置以及带宽等的限制，设置这个数，已经能满足绝大部分场景需要
     */
    private int bufferWriterQueueSize = 1024 * 1024;

    /**
     * 是否使用直接内存
     */
    private boolean direct = false;

    /**
     * 流控开关，默认不打开
     */
    private boolean flowControl = false;
    /**
     * 流控阈值(高水位线)，默认与输出队列一致，则表示不做限制
     */
    private int highWaterMark = bufferWriterQueueSize;
    /**
     * 释放流控阈值(低水位线)，默认高水位的一半
     */
    private int lowWaterMark = highWaterMark / 2;

    /**
     * 设置Socket的TCP参数配置
     * AIO服户端的可选为：
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
     * <p>
     * <p>
     * AIO客户端的有效可选范围为：
     * 2. StandardSocketOptions.SO_RCVBUF
     * 4. StandardSocketOptions.SO_REUSEADDR
     */
    private Map<SocketOption<Object>, Object> socketOptions;

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

    public boolean isFlowControl() {
        return flowControl;
    }

    public void setFlowControl(boolean flowControl) {
        this.flowControl = flowControl;
    }

    public int getHighWaterMark() {
        return highWaterMark;
    }

    public void setHighWaterMark(int highWaterMark) {
        this.highWaterMark = highWaterMark;
    }

    public int getLowWaterMark() {
        return lowWaterMark;
    }

    public void setLowWaterMark(int lowWaterMark) {
        this.lowWaterMark = lowWaterMark;
    }


    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    @Override
    public String toString() {
        return "{" +
                "host='" + (host == null ? "localhost" : host) + '\'' +
                ", port=" + port +
                ", readBufferSize=" + readBufferSize +
                ", bufferWriterQueueSize=" + bufferWriterQueueSize +
                ", flowControl=" + flowControl +
                ", highWaterMark=" + highWaterMark +
                ", lowWaterMark=" + lowWaterMark +
                ", socketOptions=" + socketOptions +
                '}';
    }
}
