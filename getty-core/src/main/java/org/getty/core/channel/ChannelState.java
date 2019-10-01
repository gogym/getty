/**
 * 包名：org.getty.core.channel
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel;


/**
 * 类名：ChannelState.java
 * 描述：通道状态
 * 修改人：gogym
 * 时间：2019/9/27
 */
public enum ChannelState {
    /**
     * 新的连接
     */
    NEW_CHANNEL,
    /**
     * 连接关闭。
     */
    CHANNEL_CLOSED,
    /**
     * 读通道已关闭。
     */
    INPUT_SHUTDOWN,

    /**
     * 写通道已关闭。
     */
    OUTPUT_SHUTDOWN,


    /**
     * 读操作异常。
     */
    INPUT_EXCEPTION,
    /**
     * 写操作异常。
     */
    OUTPUT_EXCEPTION,

    /**
     * ENCODE异常。
     */
    ENCODE_EXCEPTION,

    /**
     * DECODE异常。
     */
    DECODE_EXCEPTION,

    /**
     * 读取数据
     */
    CHANNEL_READ,

    /**
     * 写数据
     */
    CHANNEL_WRITE

}
