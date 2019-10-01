/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package org.getty.core.pipeline;


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


    public Iterator<ChannelHandlerAdapter> getIterator() {
        return pipeList.iterator();
    }

    public Iterator<ChannelHandlerAdapter> getReverseIterator() {
        LinkedList<ChannelHandlerAdapter> newList = reverseLinkedList(pipeList);
        return newList.iterator();
    }


    private LinkedList<ChannelHandlerAdapter> reverseLinkedList(LinkedList<ChannelHandlerAdapter> list) {
        LinkedList<ChannelHandlerAdapter> newLinkedList = new LinkedList<>();
        for (ChannelHandlerAdapter object : list) {
            newLinkedList.add(0, object);
        }
        return newLinkedList;
    }


    /**
     * 该方法会把原集合翻转
     *
     * @param list
     * @return
     */
    private LinkedList<ChannelHandlerAdapter> reverseList(LinkedList<ChannelHandlerAdapter> list) {
        if (list == null) {
            throw new NullPointerException("无法翻转空列");
        }
        if (list.size() == 1) {
            return list;
        }
        ChannelHandlerAdapter i = list.removeFirst();
        reverseList(list).add(i);
        return list;
    }

    /**
     * 正向获取第一个处理器
     *
     * @return
     */
    public ChannelHandlerAdapter first() {
        if (pipeList != null && pipeList.size() > 0) {
            return pipeList.getFirst();
        }
        return null;
    }

    /**
     * 反向获取第一个处理器
     *
     * @return
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
     * @param channelHandlerAdapter
     * @return
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
     * @param channelHandlerAdapter
     * @return
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
     * 添加到后面
     *
     * @return void
     * @params [channelHandlerAdapter]
     */
    public void addLast(ChannelHandlerAdapter channelHandlerAdapter) {
        pipeList.addLast(channelHandlerAdapter);
    }

    /**
     * 添加到第一位
     *
     * @return void
     * @params [channelHandlerAdapter]
     */
    public void addFirst(ChannelHandlerAdapter channelHandlerAdapter) {
        pipeList.addFirst(channelHandlerAdapter);
    }


    public void clean() {
        pipeList.clear();
    }

}
