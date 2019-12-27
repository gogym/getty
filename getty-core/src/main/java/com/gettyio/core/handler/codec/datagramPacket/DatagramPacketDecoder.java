package com.gettyio.core.handler.codec.datagramPacket;/*
 * 类名：DatagramPacketDecoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/18
 */

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;
import com.gettyio.core.util.LinkedNonBlockQueue;

import java.net.DatagramPacket;

public class DatagramPacketDecoder extends ObjectToMessageDecoder {

    @Override
    public void decode(AioChannel aioChannel, Object obj, LinkedNonBlockQueue<Object> out) throws Exception {
        DatagramPacket datagramPacket = (DatagramPacket) obj;
        out.put(datagramPacket);
        super.decode(aioChannel, obj, out);
    }

}
