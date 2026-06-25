/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.util;

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * 网络工具类。
 * <p>
 * 提供 IP 校验、IP/整数互转、MAC 地址获取、CIDR 网段匹配等功能。
 * 正则表达式预编译为静态常量以提升性能。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class NetWorkUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NetWorkUtil.class);

    /**
     * 预编译的 IPv4 正则表达式（避免每次调用时重新编译）
     */
    private static final Pattern IPV4_PATTERN;

    static {
        String octet = "(2[0-4]\\d)|(25[0-5])|(1\\d{2})|([1-9]\\d)|(\\d)";
        String ipv4 = octet + "\\." + octet + "\\." + octet + "\\." + octet;
        IPV4_PATTERN = Pattern.compile(ipv4);
    }

    private NetWorkUtil() {
    }

    /**
     * 校验 IPv4 地址格式是否合法
     *
     * @param ip 待校验的 IP 字符串
     * @return {@code true} 如果格式合法
     */
    public static boolean ipValid(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 获取本机局域网 IP 地址（兼容 Windows 和 Linux）。
     * <p>
     * 优先遍历网卡获取非回环 IPv4 地址，失败时回退到 {@link InetAddress#getLocalHost()}。
     * </p>
     *
     * @return 本机 IP 地址，默认返回 "127.0.0.1"
     */
    public static String getLocalIP() {
        String localIP = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface ni = netInterfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (!ip.isLoopbackAddress() && ip.getHostAddress().indexOf(':') == -1) {
                        localIP = ip.getHostAddress();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            try {
                localIP = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e1) {
                logger.warn("Failed to get local IP address", e1);
            }
        }
        return localIP;
    }

    /**
     * 将 IPv4 字符串转换为 long 型整数
     *
     * @param strIp IPv4 地址字符串，如 "192.168.1.1"
     * @return 对应的 long 值
     */
    public static long ipToLong(String strIp) {
        String[] ip = strIp.split("\\.");
        return (Long.parseLong(ip[0]) << 24)
                + (Long.parseLong(ip[1]) << 16)
                + (Long.parseLong(ip[2]) << 8)
                + Long.parseLong(ip[3]);
    }

    /**
     * 将 long 型整数转换为 IPv4 字符串
     *
     * @param longIp IP 的 long 表示
     * @return IPv4 地址字符串
     */
    public static String longToIP(long longIp) {
        StringBuilder sb = new StringBuilder(15);
        sb.append(longIp >>> 24);
        sb.append('.');
        sb.append((longIp & 0x00FFFFFF) >>> 16);
        sb.append('.');
        sb.append((longIp & 0x0000FFFF) >>> 8);
        sb.append('.');
        sb.append(longIp & 0x000000FF);
        return sb.toString();
    }

    /**
     * 获取本机 MAC 地址
     *
     * @return MAC 地址字符串（大写，以 "-" 分隔），如 "AA-BB-CC-DD-EE-FF"
     * @throws Exception 获取失败时抛出
     */
    public static String getLocalMACAddress() throws Exception {
        InetAddress ia = InetAddress.getLocalHost();
        return getMACAddress(ia);
    }

    /**
     * 获取指定网络接口的 MAC 地址
     *
     * @param ia InetAddress 对象
     * @return MAC 地址字符串
     * @throws Exception 获取失败时抛出
     */
    public static String getMACAddress(InetAddress ia) throws Exception {
        byte[] mac = NetworkInterface.getByInetAddress(ia).getHardwareAddress();
        StringBuilder sb = new StringBuilder(mac.length * 3);
        for (int i = 0; i < mac.length; i++) {
            if (i != 0) {
                sb.append('-');
            }
            String s = Integer.toHexString(mac[i] & 0xFF);
            sb.append(s.length() == 1 ? '0' + s : s);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 判断 IP 是否在指定 CIDR 网段内
     * <p>
     * 示例：{@code isInRange("192.168.1.127", "192.168.1.64/26")}
     * </p>
     *
     * @param ip   待判断的 IP 地址
     * @param cidr CIDR 格式的网段，如 "192.168.1.0/24"
     * @return {@code true} 如果 IP 在网段内
     */
    public static boolean isInRange(String ip, String cidr) {
        String[] ips = ip.split("\\.");
        int ipAddr = (Integer.parseInt(ips[0]) << 24)
                | (Integer.parseInt(ips[1]) << 16)
                | (Integer.parseInt(ips[2]) << 8)
                | Integer.parseInt(ips[3]);

        int type = Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1));
        int mask = 0xFFFFFFFF << (32 - type);

        String cidrIp = cidr.substring(0, cidr.indexOf('/'));
        String[] cidrIps = cidrIp.split("\\.");
        int cidrIpAddr = (Integer.parseInt(cidrIps[0]) << 24)
                | (Integer.parseInt(cidrIps[1]) << 16)
                | (Integer.parseInt(cidrIps[2]) << 8)
                | Integer.parseInt(cidrIps[3]);

        return (ipAddr & mask) == (cidrIpAddr & mask);
    }
}
