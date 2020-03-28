/**
 * 包名：org.getty.core.channel.server
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.channel.config;

/**
 * 类名：AioServerConfig.java
 * 描述：服务器端配置
 * 修改人：gogym
 * 时间：2019/9/27
 */
public final class ServerConfig extends BaseConfig {

    //服务器端内存池大小,默认256m
    private Integer serverChunkSize = 256 * 1024 * 1024;

    public Integer getServerChunkSize() {
        return serverChunkSize;
    }

    public void setServerChunkSize(Integer serverChunkSize) {
        this.serverChunkSize = serverChunkSize;
    }
}
