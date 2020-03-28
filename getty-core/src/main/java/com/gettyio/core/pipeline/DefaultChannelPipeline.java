/**
 * 包名：org.getty.core.pipeline
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.pipeline;


import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.channel.UdpChannel;
import com.gettyio.core.pipeline.all.ChannelAllBoundHandlerAdapter;
import com.gettyio.core.pipeline.in.ChannelInboundHandlerAdapter;
import com.gettyio.core.pipeline.out.ChannelOutboundHandlerAdapter;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * 类名：DefaultChannelPipeline.java
 * 描述：责任链对象
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class DefaultChannelPipeline {
    //入站链
    LinkedList<ChannelHandlerAdapter> inPipeList;
    //出站链
    LinkedList<ChannelHandlerAdapter> outPipeList;

    SocketChannel socketChannel;

    public DefaultChannelPipeline(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        if (inPipeList == null) {
            inPipeList = new LinkedList<>();
        }
        if (outPipeList == null) {
            outPipeList = new LinkedList<>();
        }
    }


    //获取入站责任链
    public Iterator<ChannelHandlerAdapter> getInPipeList() {
        return inPipeList.iterator();
    }

    //获取责任链
    public Iterator<ChannelHandlerAdapter> getOutPipeList() {
        LinkedList<ChannelHandlerAdapter> newList = reverseLinkedList(outPipeList);
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
     * 获取第一个入站处理器
     *
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter inPipeFirst() {
        if (inPipeList != null && inPipeList.size() > 0) {
            return inPipeList.getFirst();
        }
        return null;
    }

    /**
     * 获取第一个出站处理器
     *
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter outPipeFirst() {
        if (outPipeList != null && outPipeList.size() > 0) {
            return outPipeList.getLast();
        }
        return null;
    }


    /**
     * 获取下一个入站处理器
     *
     * @param channelHandlerAdapter 当前处理器
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter nextInPipe(ChannelHandlerAdapter channelHandlerAdapter) {
        int index = inPipeList.indexOf(channelHandlerAdapter);
        index++;
        if (inPipeList.size() > index) {
            return inPipeList.get(index);
        }
        return null;
    }


    /**
     * 获取下一个出站处理器
     *
     * @param channelHandlerAdapter 当前处理器
     * @return ChannelHandlerAdapter
     */
    public ChannelHandlerAdapter nextOutPipe(ChannelHandlerAdapter channelHandlerAdapter) {
        int index = outPipeList.indexOf(channelHandlerAdapter);
        index--;
        if (index >= 0) {
            return outPipeList.get(index);
        }
        return null;
    }

    /**
     * 添加到最后一位
     *
     * @param channelHandlerAdapter 当前处理器
     */
    public void addLast(ChannelHandlerAdapter channelHandlerAdapter) {
        if (socketChannel instanceof UdpChannel && !(channelHandlerAdapter instanceof DatagramPacketHandler)) {
            //如果是udp模式，则有些处理器是不适合udp使用的，不加入
            return;
        }
        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            inPipeList.addLast(channelHandlerAdapter);
        } else if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            outPipeList.addLast(channelHandlerAdapter);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            inPipeList.addLast(channelHandlerAdapter);
            outPipeList.addLast(channelHandlerAdapter);
        }
    }

    /**
     * 添加到第一位
     *
     * @param channelHandlerAdapter 当前处理器
     */
    public void addFirst(ChannelHandlerAdapter channelHandlerAdapter) {
        if (socketChannel instanceof UdpChannel && !(channelHandlerAdapter instanceof DatagramPacketHandler)) {
            //如果是udp模式，则有些处理器是不适合udp使用的，不加入
            return;
        }

        if (channelHandlerAdapter instanceof ChannelInboundHandlerAdapter) {
            inPipeList.addFirst(channelHandlerAdapter);
        } else if (channelHandlerAdapter instanceof ChannelOutboundHandlerAdapter) {
            outPipeList.addFirst(channelHandlerAdapter);
        } else if (channelHandlerAdapter instanceof ChannelAllBoundHandlerAdapter) {
            inPipeList.addFirst(channelHandlerAdapter);
            outPipeList.addFirst(channelHandlerAdapter);
        }
    }


    /**
     * 清理责任链
     */
    public void clean() {
        inPipeList.clear();
        outPipeList.clear();
    }

}
