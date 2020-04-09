/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * DefaultChannelPipeline.java
 *
 * @description:默认责任链对象
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class DefaultChannelPipeline {
    /**
     * 入栈链
     */
    LinkedList<ChannelHandlerAdapter> inPipeList;
    /**
     * 出栈链
     */
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


    /**
     * 获取入栈责任链
     *
     * @return
     */
    public Iterator<ChannelHandlerAdapter> getInPipeList() {
        return inPipeList.iterator();
    }


    /**
     * 获取出栈责任链
     *
     * @return
     */
    public Iterator<ChannelHandlerAdapter> getOutPipeList() {
        LinkedList<ChannelHandlerAdapter> newList = reverseLinkedList(outPipeList);
        return newList.iterator();
    }


    /**
     * 翻转集合
     *
     * @param list
     * @return
     */
    private LinkedList<ChannelHandlerAdapter> reverseLinkedList(LinkedList<ChannelHandlerAdapter> list) {
        LinkedList<ChannelHandlerAdapter> newLinkedList = new LinkedList<>();
        for (ChannelHandlerAdapter object : list) {
            newLinkedList.add(0, object);
        }
        return newLinkedList;
    }

    /**
     * 获取第一个入栈处理器
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
     * 获取第一个出栈处理器
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
     * 获取下一个入栈处理器
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
     * 获取下一个出栈处理器
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
