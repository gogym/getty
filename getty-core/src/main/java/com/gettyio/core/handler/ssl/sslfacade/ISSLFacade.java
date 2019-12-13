package com.gettyio.core.handler.ssl.sslfacade;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface ISSLFacade
{
    void setHandshakeCompletedListener(IHandshakeCompletedListener hcl);

    void setSSLListener(ISSLListener l);

    void setCloseListener(ISessionClosedListener l);

    void beginHandshake() throws IOException;

    boolean isHandshakeCompleted();

    void encrypt(ByteBuffer plainData) throws SSLException;

    void decrypt(ByteBuffer encryptedData) throws SSLException;

    void close();

    boolean isCloseCompleted();
    
    boolean isClientMode();

    void terminate();
}
