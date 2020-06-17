package com.gettyio.core.channel.loop;

import com.gettyio.core.buffer.BufferWriter;

public interface EventLoop {

    void run();

    void shutdown();

    SelectedSelector getSelector();

    BufferWriter getBufferWriter();
}
