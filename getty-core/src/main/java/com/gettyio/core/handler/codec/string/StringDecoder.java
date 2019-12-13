/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com 
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.ByteToMessageDecoder;

import java.io.UnsupportedEncodingException;

/**
 * 类名：StringDecoder.java
 * 描述：byte转字符串对象解码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class StringDecoder extends ByteToMessageDecoder {

    @Override
    public void decode(AioChannel aioChannel, byte[] bytes) {
        try {
            String str = new String(bytes, "utf-8");
            //写到read通道里
            channelRead(aioChannel, str);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
