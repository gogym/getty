package org.getty.core.handler.ssl.sslfacade;

import javax.net.ssl.SSLException;

public interface ITasks
{
    Runnable next();

    void done() throws SSLException;
}
