/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import java.util.Iterator;
import java.util.LinkedList;

/**
 * 类名：DefaultChannelPipeline.java
 * 描述：责任链对象
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultChannelPipeline {
    LinkedList<ChannelHandlerAdapter> pipeList;

    public DefaultChannelPipeline() {
        if (pipeList == null) {
            pipeList = new LinkedList<>();
        }
    }


    //正向获取责任链
    public Iterator<ChannelHandlerAdapter> getIterator() {
        return pipeList.iterator();
    }

    //反向获取责任链
    public Iterator<ChannelHandlerAdapter> getReverseIterator() {
        LinkedList<ChannelHandlerAdapter> newList = reverseLinkedList(pipeList);
        return newList.iterator();
    }


    //翻转集合
    private LinkedList<ChannelHandlerAdapter> reverseLinkedList(LinkedList<ChannelHandlerAdapter> list) {
        LinkedList<ChannelHandlerAdapter> newLinkedList = new LinkedList<>();
        for (ChannelHandlerAdapter object : list) {
            newLinkedList.add(0, object);
        }
        return newLinkedList;
    }

    /**
     * 正向获取第一个处理器
     *
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter first() {
        if (pipeList != null && pipeList.size() > 0) {
            return pipeList.getFirst();
        }
        return null;
    }

    /**
     * 获取最后一个处理器
     *
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter last() {
        if (pipeList != null && pipeList.size() > 0) {
            return pipeList.getLast();
        }
        return null;
    }


    /**
     * 正向获取下一个处理器
     *
     * @param channelHandlerAdapter 当前处理器
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter nextOne(ChannelHandlerAdapter channelHandlerAdapter) {
        int index = pipeList.indexOf(channelHandlerAdapter);
        index++;
        if (pipeList.size() > index) {
            return pipeList.get(index);
        }
        return null;
    }


    /**
     * 反向获取下一个处理器
     *
     * @param channelHandlerAdapter 当前处理器
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter lastOne(ChannelHandlerAdapter channelHandlerAdapter) {
        int index = pipeList.indexOf(channelHandlerAdapter);
        index--;
        if (index >= 0) {
            return pipeList.get(index);
        }
        return null;
    }

    /**
     * 添加到最后一位
     *
     * @param channelHandlerAdapter 当前处理器
     */
    public void addLast(ChannelHandlerAdapter channelHandlerAdapter) {
        pipeList.addLast(channelHandlerAdapter);
    }

    /**
     * 添加到第一位
     *
     * @param channelHandlerAdapter 当前处理器
     */
    public void addFirst(ChannelHandlerAdapter channelHandlerAdapter) {
        pipeList.addFirst(channelHandlerAdapter);
    }


    /**
     * 清理责任链
     */
    public void clean() {
        pipeList.clear();
    }

}
