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
 * 通道配置基类。
 * <p>
 * 定义客户端和服务端共有的配置项，包括地址、缓冲区大小、流控参数和 Socket 选项等。
 * </p>
 *
 * @author gogym
 */
public abstract class BaseConfig {

    /** 服务器地址 */
    private String host;

    /** 服务器端口 */
    private int port;

    /** 读缓冲区大小（字节），默认 32KB */
    private int readBufferSize = 32 * 1024;

    /** 写队列容量，默认 1M */
    private int bufferWriterQueueSize = 1024 * 1024;

    /** 是否使用直接内存（DirectByteBuffer），默认堆内存 */
    private boolean direct;

    /** 流控开关，默认关闭 */
    private boolean flowControl;

    /** 流控高水位线（队列中待写数据量达到此值时暂停写入） */
    private int highWaterMark = bufferWriterQueueSize;

    /** 流控低水位线（队列中待写数据量降至此值时恢复写入） */
    private int lowWaterMark = highWaterMark / 2;

    /**
     * Socket 选项配置。
     * <p>
     * 常用选项：
     * <ul>
     *   <li>{@code SO_SNDBUF} - 发送缓冲区大小</li>
     *   <li>{@code SO_RCVBUF} - 接收缓冲区大小</li>
     *   <li>{@code SO_KEEPALIVE} - 保持连接活跃</li>
     *   <li>{@code SO_REUSEADDR} - 重用地址</li>
     *   <li>{@code TCP_NODELAY} - 禁用 Nagle 算法</li>
     * </ul>
     * </p>
     */
    private Map<SocketOption<Object>, Object> socketOptions;

    // ==================== Getters / Setters ====================

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

    public void setOption(SocketOption socketOption, Object value) {
        if (socketOptions == null) {
            socketOptions = new HashMap<>(8);
        }
        socketOptions.put(socketOption, value);
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
        return "{host='" + (host == null ? "localhost" : host) + '\'' +
                ", port=" + port +
                ", readBufferSize=" + readBufferSize +
                ", bufferWriterQueueSize=" + bufferWriterQueueSize +
                ", direct=" + direct +
                ", flowControl=" + flowControl +
                ", highWaterMark=" + highWaterMark +
                ", lowWaterMark=" + lowWaterMark +
                ", socketOptions=" + socketOptions +
                '}';
    }
}
