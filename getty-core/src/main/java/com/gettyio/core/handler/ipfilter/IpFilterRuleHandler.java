/**
 * 包名：org.getty.core.handler.ipfilter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.ipfilter;

import java.net.InetSocketAddress;
import java.util.List;

import com.gettyio.core.channel.AioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 类名：IpFilterRuleHandler.java
 * 描述：ip过滤器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class IpFilterRuleHandler extends AbstractRemoteAddressFilter<InetSocketAddress> {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedIpFilter.class);

    IpFilterRule rules;

    public IpFilterRuleHandler(List<IpRange> ips,IpFilterRuleType ipFilterRuleType) {
        if (ips == null) {
            throw new NullPointerException("rules");
        }
        rules = new RuleBasedIpFilter(ips,ipFilterRuleType);
    }


    @Override
    protected boolean accept(AioChannel aioChannel, InetSocketAddress remoteAddress) {

        if (rules.matches(remoteAddress)) {
            return rules.ruleType() == IpFilterRuleType.ACCEPT;
        }
        return true;
    }
}
