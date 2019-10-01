package org.getty.core.handler.ipfilter;

/**
 * Used in {@link IpFilterRule} to decide if a matching IP Address should be allowed or denied to connect.
 */
public enum IpFilterRuleType {
    ACCEPT,
    REJECT
}
