package com.gettyio.core.channel.starter;/*
 * 类名：Starter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/4/8
 */

public abstract class Starter {

    /**
     * Boss线程数，获取cpu核心,核心小于4设置线程为3，大于4设置和cpu核心数一致
     */
    protected int bossThreadNum = Runtime.getRuntime().availableProcessors() < 4 ? 3 : Runtime.getRuntime().availableProcessors();
    /**
     * Boss共享给Worker的线程数，核心小于4设置线程为1，大于4右移两位
     */
    private int bossShareToWorkerThreadNum = bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2;
    /**
     * Worker线程数
     */
    protected int workerThreadNum = bossThreadNum - bossShareToWorkerThreadNum;

}
