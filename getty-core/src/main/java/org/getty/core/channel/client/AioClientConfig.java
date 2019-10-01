/**
 * 包名：org.getty.core.channel.client
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.client;

import org.getty.core.channel.AioConfig;

/**
 * 类名：AioClientConfig.java
 * 描述：客户端配置
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class AioClientConfig extends AioConfig {

    //内存大小
    public Integer clientPageSize = 1024 * 256;

    public Integer getClientPageSize() {
        return clientPageSize;
    }

    public void setClientPageSize(Integer clientPageSize) {
        this.clientPageSize = clientPageSize;
    }
}
