/**
 * 包名：org.getty.core.channel.server
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.channel.server;

import org.getty.core.channel.AioConfig;

import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 类名：AioServerConfig.java
 * 描述：服务器端配置
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class AioServerConfig extends AioConfig {

    //服务器端内存大小
    private Integer serverPageSize = 1024 * 1024 * 16;

    public Integer getServerPageSize() {
        return serverPageSize;
    }

    public void setServerPageSize(Integer serverPageSize) {
        this.serverPageSize = serverPageSize;
    }
}
