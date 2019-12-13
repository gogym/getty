package com.gettyio.core.handler.ssl.sslfacade;

import java.nio.ByteBuffer;

public class BufferUtils
{
    static void copy(ByteBuffer from, ByteBuffer to)
    {
        to.put(from);
        to.flip();
    }

    public static ByteBuffer slice(ByteBuffer data)
    {
        if (data.hasRemaining())
        {
            byte[] slice = new byte[data.remaining()];
            data.get(slice, 0, data.remaining());
            return ByteBuffer.wrap(slice);
        }
        return null;
    }
}
