/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.AioChannel;

/**
 * 类名：ChannelboundHandler.java
 * 描述：
 * 修改人：gogym
 * 时间：2019/9/27
 */
public interface ChannelboundHandler {

    /**
     * 异常
     * @param aioChannel 通道
     * @param pipelineDirection 责任链
     * @param cause 异常信息
     */
    void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) throws Exception;
}
