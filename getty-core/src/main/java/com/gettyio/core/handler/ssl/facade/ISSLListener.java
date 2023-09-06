package com.gettyio.core.handler.ssl.facade;

import java.nio.ByteBuffer;

public interface ISSLListener {
    void onWrappedData(ByteBuffer wrappedBytes);

    void onPlainData(ByteBuffer plainBytes);
}
