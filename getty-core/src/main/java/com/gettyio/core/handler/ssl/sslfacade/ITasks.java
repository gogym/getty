package com.gettyio.core.handler.ssl.sslfacade;

import javax.net.ssl.SSLException;

public interface ITasks
{
    Runnable next();

    void done() throws SSLException;
}
