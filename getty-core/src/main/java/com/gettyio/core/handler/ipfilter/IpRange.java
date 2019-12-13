/**
 * 包名：org.getty.core.handler.ipfilter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */

package com.gettyio.core.handler.ipfilter;

/**
 * 类名：IpRange.java
 * 描述：包装起止ip段
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class IpRange {

    // 开始ip
    private String ipStart;

    // 结束ip
    private String ipEnd;

    public IpRange(String ipStart, String ipEnd) {

        this.ipStart = ipStart;
        this.ipEnd = ipEnd;
    }

    public String getIpStart() {

        return ipStart;
    }

    public String getIpEnd() {

        return ipEnd;
    }

}
