/**
 * 包名：org.getty.core.handler.ipfilter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.ipfilter;

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.NetWorkUtil;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Ip规则过滤器
 *
 * @author gogym
 * @version 2019年7月13日
 * @see RuleBasedIpFilter
 */
public class RuleBasedIpFilter implements IpFilterRule {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RuleBasedIpFilter.class);

    List<IpRange> ips;
    IpFilterRuleType ipFilterRuleType;

    public RuleBasedIpFilter(List<IpRange> ips, IpFilterRuleType ipFilterRuleType) {
        if (ips == null) {
            logger.warn("blackIps was null");
        }
        this.ips = ips;
        this.ipFilterRuleType = ipFilterRuleType;
    }


    @Override
    public boolean matches(InetSocketAddress remoteAddress) {

        if (ips == null) {
            return true;
        }
        // ip转成long类型
        String ip = remoteAddress.getHostString();
        long ipLong = NetWorkUtil.ipToLong(ip);

        for (IpRange ipRange : ips) {
            long ipStart = NetWorkUtil.ipToLong(ipRange.getIpStart());
            long ipEnd = NetWorkUtil.ipToLong(ipRange.getIpEnd());
            // 比较ip区间
            if (ipLong >= ipStart && ipLong <= ipEnd) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IpFilterRuleType ruleType() {
        // 返回拒绝则表示拒绝连接，返回接受则表示可以连接
        return ipFilterRuleType;
    }

}
