/**
 * 包名：org.getty.core.pipeline.in
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline.in;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.pipeline.ChannelHandlerAdapter;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.handler.timeout.IdleState;
import com.gettyio.core.util.LinkedNonBlockQueue;


/**
 * 类名：ChannelInboundHandlerAdapter.java
 * 描述：入栈器父类
 * 修改人：gogym
 * 时间：2019/9/27
 */
public abstract class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler {


}
