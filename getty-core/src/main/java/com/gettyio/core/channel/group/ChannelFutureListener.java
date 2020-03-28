/**
 * 包名：org.getty.core.channel.group
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.group;

import com.gettyio.core.channel.SocketChannel;

/**
 * 类名：ChannelFutureListener.java
 * 描述：通道监听，目前用于监听关闭时清理
 * 修改人：gogym
 * 时间：2019/9/27
 */
public interface ChannelFutureListener {

    void operationComplete(SocketChannel socketChannel);

}
