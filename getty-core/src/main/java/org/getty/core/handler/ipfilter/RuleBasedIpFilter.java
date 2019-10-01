/**
 * 包名：org.getty.core.handler.ipfilter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.handler.ipfilter;

import org.getty.core.util.NetWorkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Ip规则过滤器
 *
 * @author gogym
 * @version 2019年7月13日
 * @see RuleBasedIpFilter
 * @since
 */
public class RuleBasedIpFilter implements IpFilterRule {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedIpFilter.class);

    List<IpRange> blackIps;

    public RuleBasedIpFilter(List<IpRange> blackIps) {
        if (blackIps == null) {
            logger.warn("blackIps was null");
        }
        this.blackIps = blackIps;
    }


    @Override
    public boolean matches(InetSocketAddress remoteAddress) {

        if (blackIps == null) {
            return true;
        }
        // ip转成long类型
        String ip = remoteAddress.getHostString();
        long ipLong = NetWorkUtil.ipToLong(ip);

        for (IpRange ipRange : blackIps) {
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
        return IpFilterRuleType.REJECT;
    }

}
