/**
 * 包名：org.getty.core.handler.codec.string
 * 版权：Copyright by www.getty.com
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.handler.codec.string;

import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.ObjectToMessageDecoder;

import java.io.UnsupportedEncodingException;

/**
 * 类名：StringDecoder.java
 * 描述：byte转字符串对象解码器
 * 修改人：gogym
 * 时间：2019/9/27
 */
public class StringDecoder extends ObjectToMessageDecoder {

    @Override
    public void decode(AioChannel aioChannel, Object obj) {
        try {
            String str = new String((byte[]) obj, "utf-8");
            //写到read通道里
            channelRead(aioChannel, str);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
