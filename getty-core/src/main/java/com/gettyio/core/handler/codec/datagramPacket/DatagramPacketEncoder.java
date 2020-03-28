package com.gettyio.core.handler.codec.datagrampacket;
/*
 * 类名：DatagramPacketEncoder
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/18
 */

import com.gettyio.core.channel.SocketChannel;
import com.gettyio.core.handler.codec.MessageToByteEncoder;
import com.gettyio.core.pipeline.DatagramPacketHandler;


public class DatagramPacketEncoder extends MessageToByteEncoder implements DatagramPacketHandler {

    @Override
    public void encode(SocketChannel socketChannel, Object obj) throws Exception {
        //udp包直接由通道发出，实际这里并没有处理什么
        super.encode(socketChannel, obj);
    }
}
