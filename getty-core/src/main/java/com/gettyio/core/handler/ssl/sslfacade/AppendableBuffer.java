package com.gettyio.core.handler.ssl.sslfacade;

import java.nio.ByteBuffer;

class AppendableBuffer
{
    private ByteBuffer b;

    public ByteBuffer append(ByteBuffer data)
    {
        ByteBuffer nb = ByteBuffer.allocate(calculateSize(data));
        if (notNull())
        {
            nb.put(b);
            clear();
        }
        nb.put(data);
        return nb;
    }

    public void set(ByteBuffer data)
    {
        if (data.hasRemaining())
        {
            b = ByteBuffer.allocate(data.remaining());
            b.put(data);
            b.rewind();
        }
    }

    public void clear()
    {
        b = null;
    }

    /* private */

    private int calculateSize(ByteBuffer data)
    {
        int result = data.limit();
        if (notNull())
        {
            result += b.capacity();
        }
        return result;
    }

    private boolean notNull()
    {
        return b != null;
    }

    public boolean hasRemaining()
    {
        if (notNull())
        {
            return b.hasRemaining();
        }
        return false;
    }

    public ByteBuffer get()
    {
        return b;
    }
}
