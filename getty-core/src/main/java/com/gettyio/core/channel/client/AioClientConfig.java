/**
 * 包名：org.getty.core.channel.client
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.client;

import com.gettyio.core.channel.AioConfig;

/**
 * 类名：AioClientConfig.java
 * 描述：客户端配置
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class AioClientConfig extends AioConfig {

    //内存池大小,默认256
    public Integer clientChunkSize = 256 * 1024 * 1024;

    public Integer getClientChunkSize() {
        return clientChunkSize;
    }

    public void setClientChunkSize(Integer clientChunkSize) {
        this.clientChunkSize = clientChunkSize;
    }
}
