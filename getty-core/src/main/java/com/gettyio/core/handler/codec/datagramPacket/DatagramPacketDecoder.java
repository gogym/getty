package com.gettyio.core.handler.codec.datagrampacket;/*
 * 类名：DatagramPacketDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/18
 */

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.pipeline.DatagramPacketHandler;
import com.gettyio.core.util.LinkedNonBlockQueue;

import java.net.DatagramPacket;

public class DatagramPacketDecoder extends ObjectToMessageDecoder implements DatagramPacketHandler {

    @Override
    public void decode(SocketChannel socketChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        DatagramPacket datagramPacket = (DatagramPacket) obj;
        out.put(datagramPacket);
        super.decode(socketChannel, obj, out);
    }

}
